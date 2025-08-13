package com.dbVybe.app.service.agent;

import com.dbVybe.app.domain.model.DatabaseType;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.JsonWithInt;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.LocalDateTime;

/**
 * Database Schema Agent - Analyzes database schemas and creates vector embeddings
 * 
 * This agent:
 * - Connects to user databases and analyzes their schema structure
 * - Generates vector embeddings for tables, columns, and relationships
 * - Stores embeddings in Qdrant vector database for semantic search
 * - Provides schema insights and recommendations
 */
@Service
public class DatabaseSchemaAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaAgent.class);
    
    // Qdrant Configuration
    @Value("${qdrant.url:https://your-cluster-url.qdrant.io:6334}")
    private String qdrantUrl;
    
    @Value("${qdrant.api-key}")
    private String qdrantApiKey;
    
    @Value("${qdrant.collection.name:dbvybe_schemas}")
    private String collectionName;
    
    // Embedding Configuration
    private EmbeddingModel embeddingModel;
    private QdrantClient qdrantClient;
    
    // Schema Analysis Statistics
    private long totalSchemasAnalyzed = 0;
    private long totalTablesProcessed = 0;
    private long totalEmbeddingsGenerated = 0;
    
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing Database Schema Agent with Qdrant integration");
            
            // Initialize embedding model (local model for better performance)
            this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            
            // Initialize Qdrant client
            if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
                this.qdrantClient = new QdrantClient(
                    QdrantGrpcClient.newBuilder(qdrantUrl, 6334, true)
                        .withApiKey(qdrantApiKey)
                        .build()
                );
                
                // Ensure collection exists
                ensureCollectionExists();
            } else {
                logger.warn("Qdrant API key not configured. Schema analysis will work but embeddings won't be stored.");
            }
            
            logger.info("Database Schema Agent initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize Database Schema Agent: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Analyze database schema and generate vector embeddings
     */
    public CompletableFuture<SchemaAnalysisResult> analyzeSchema(
            String connectionId,
            String userId,
            DatabaseType databaseType,
            Connection connection) {
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                logger.info("Starting schema analysis for connection {} (user: {}, type: {})", 
                    connectionId, userId, databaseType);
                
                // Extract schema information based on database type
                SchemaInfo schemaInfo = extractSchemaInfo(connection, databaseType);
                
                // Generate embeddings for schema components
                List<SchemaEmbedding> embeddings = generateSchemaEmbeddings(schemaInfo, connectionId, userId);
                
                // Store embeddings in Qdrant
                if (qdrantClient != null) {
                    storeEmbeddings(embeddings);
                }
                
                // Update statistics
                totalSchemasAnalyzed++;
                totalTablesProcessed += schemaInfo.getTables().size();
                totalEmbeddingsGenerated += embeddings.size();
                
                long processingTime = System.currentTimeMillis() - startTime;
                
                logger.info("Schema analysis completed for connection {} in {}ms. Tables: {}, Embeddings: {}", 
                    connectionId, processingTime, schemaInfo.getTables().size(), embeddings.size());
                
                return new SchemaAnalysisResult(
                    connectionId,
                    userId,
                    databaseType,
                    schemaInfo,
                    embeddings.size(),
                    true,
                    null,
                    processingTime
                );
                
            } catch (Exception e) {
                long processingTime = System.currentTimeMillis() - startTime;
                logger.error("Schema analysis failed for connection {}: {}", connectionId, e.getMessage(), e);
                
                return new SchemaAnalysisResult(
                    connectionId,
                    userId,
                    databaseType,
                    null,
                    0,
                    false,
                    e.getMessage(),
                    processingTime
                );
            }
        });
    }
    
    /**
     * Search for similar schemas using vector similarity
     */
    public CompletableFuture<List<SimilarSchema>> findSimilarSchemas(String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (qdrantClient == null) {
                    logger.warn("Qdrant client not available. Cannot search for similar schemas.");
                    return Collections.emptyList();
                }
                
                // Generate embedding for the query
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                
                // Search in Qdrant
                SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryEmbedding.vectorAsList())
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();
                
                ListenableFuture<List<ScoredPoint>> searchFuture = qdrantClient.searchAsync(searchRequest);
                List<ScoredPoint> searchResults = searchFuture.get();
                
                List<SimilarSchema> results = new ArrayList<>();
                for (ScoredPoint point : searchResults) {
                    @SuppressWarnings("deprecation")
                    Map<String, JsonWithInt.Value> payload = point.getPayload();
                    
                    SimilarSchema schema = new SimilarSchema(
                        getStringFromValue(payload.get("connectionId")),
                        getStringFromValue(payload.get("userId")),
                        getStringFromValue(payload.get("tableName")),
                        getStringFromValue(payload.get("description")),
                        point.getScore()
                    );
                    results.add(schema);
                }
                
                logger.info("Found {} similar schemas for query: {}", results.size(), query);
                return results;
                
            } catch (Exception e) {
                logger.error("Error searching for similar schemas: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        });
    }
    
    /**
     * Delete all schema embeddings for a specific database connection
     */
    public CompletableFuture<Boolean> deleteSchemaEmbeddings(String connectionId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (qdrantClient == null) {
                    logger.warn("Qdrant client not available. Cannot delete schema embeddings for connection: {}", connectionId);
                    return false;
                }
                
                logger.info("Deleting schema embeddings for connection {} (user: {})", connectionId, userId);
                
                // For now, use a simple approach - search for points and then delete by IDs
                // This is a workaround for complex filter API issues
                SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(Collections.nCopies(384, 0.0f)) // Dummy vector for search
                    .setLimit(10000) // Large limit to get all points
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();
                
                ListenableFuture<List<ScoredPoint>> searchFuture = qdrantClient.searchAsync(searchRequest);
                List<ScoredPoint> allPoints = searchFuture.get();
                
                // Filter points that match our connectionId and userId
                List<PointId> pointsToDelete = new ArrayList<>();
                for (ScoredPoint point : allPoints) {
                    @SuppressWarnings("deprecation")
                    Map<String, JsonWithInt.Value> payload = point.getPayload();
                    
                    String pointConnectionId = getStringFromValue(payload.get("connectionId"));
                    String pointUserId = getStringFromValue(payload.get("userId"));
                    
                    if (connectionId.equals(pointConnectionId) && userId.equals(pointUserId)) {
                        pointsToDelete.add(point.getId());
                    }
                }
                
                if (!pointsToDelete.isEmpty()) {
                    // Delete the matched points
                    DeletePoints deleteRequest = DeletePoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setPoints(PointsSelector.newBuilder().setPoints(PointsIdsList.newBuilder().addAllIds(pointsToDelete).build()).build())
                        .build();
                    
                    ListenableFuture<UpdateResult> deleteFuture = qdrantClient.deleteAsync(deleteRequest);
                    deleteFuture.get(); // Wait for completion
                    
                    logger.info("Successfully deleted {} schema embeddings for connection {} (user: {})", 
                        pointsToDelete.size(), connectionId, userId);
                } else {
                    logger.info("No schema embeddings found for connection {} (user: {})", connectionId, userId);
                }
                
                return true;
                
            } catch (Exception e) {
                logger.error("Error deleting schema embeddings for connection {} (user: {}): {}", 
                    connectionId, userId, e.getMessage(), e);
                return false;
            }
        });
    }
    
    /**
     * Get schema analysis statistics
     */
    public SchemaAgentStats getStats() {
        return new SchemaAgentStats(
            totalSchemasAnalyzed,
            totalTablesProcessed,
            totalEmbeddingsGenerated,
            qdrantClient != null
        );
    }
    
    /**
     * Extract schema information from database connection
     */
    private SchemaInfo extractSchemaInfo(Connection connection, DatabaseType databaseType) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<TableInfo> tables = new ArrayList<>();
        
        // Get all tables
        try (ResultSet tablesRs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tablesRs.next()) {
                String tableName = tablesRs.getString("TABLE_NAME");
                String tableComment = tablesRs.getString("REMARKS");
                
                // Get columns for this table
                List<ColumnInfo> columns = getTableColumns(metaData, tableName);
                
                // Get foreign keys
                List<ForeignKeyInfo> foreignKeys = getForeignKeys(metaData, tableName);
                
                TableInfo tableInfo = new TableInfo(tableName, tableComment, columns, foreignKeys);
                tables.add(tableInfo);
            }
        }
        
        return new SchemaInfo(databaseType, tables);
    }
    
    /**
     * Get column information for a table
     */
    private List<ColumnInfo> getTableColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        
        try (ResultSet columnsRs = metaData.getColumns(null, null, tableName, "%")) {
            while (columnsRs.next()) {
                String columnName = columnsRs.getString("COLUMN_NAME");
                String dataType = columnsRs.getString("TYPE_NAME");
                int columnSize = columnsRs.getInt("COLUMN_SIZE");
                boolean nullable = "YES".equals(columnsRs.getString("IS_NULLABLE"));
                String defaultValue = columnsRs.getString("COLUMN_DEF");
                String comment = columnsRs.getString("REMARKS");
                
                ColumnInfo columnInfo = new ColumnInfo(
                    columnName, dataType, columnSize, nullable, defaultValue, comment
                );
                columns.add(columnInfo);
            }
        }
        
        return columns;
    }
    
    /**
     * Get foreign key information for a table
     */
    private List<ForeignKeyInfo> getForeignKeys(DatabaseMetaData metaData, String tableName) throws SQLException {
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        
        try (ResultSet fkRs = metaData.getImportedKeys(null, null, tableName)) {
            while (fkRs.next()) {
                String fkColumnName = fkRs.getString("FKCOLUMN_NAME");
                String pkTableName = fkRs.getString("PKTABLE_NAME");
                String pkColumnName = fkRs.getString("PKCOLUMN_NAME");
                
                ForeignKeyInfo fkInfo = new ForeignKeyInfo(fkColumnName, pkTableName, pkColumnName);
                foreignKeys.add(fkInfo);
            }
        }
        
        return foreignKeys;
    }
    
    /**
     * Generate embeddings for schema components
     */
    private List<SchemaEmbedding> generateSchemaEmbeddings(SchemaInfo schemaInfo, String connectionId, String userId) {
        List<SchemaEmbedding> embeddings = new ArrayList<>();
        
        for (TableInfo table : schemaInfo.getTables()) {
            // Create description for the table
            String tableDescription = buildTableDescription(table);
            
            // Generate embedding
            Embedding embedding = embeddingModel.embed(tableDescription).content();
            
            SchemaEmbedding schemaEmbedding = new SchemaEmbedding(
                UUID.randomUUID().toString(),
                connectionId,
                userId,
                table.getTableName(),
                tableDescription,
                embedding.vectorAsList(),
                LocalDateTime.now()
            );
            
            embeddings.add(schemaEmbedding);
        }
        
        return embeddings;
    }
    
    /**
     * Build natural language description of a table for embedding
     */
    private String buildTableDescription(TableInfo table) {
        StringBuilder description = new StringBuilder();
        
        description.append("Table: ").append(table.getTableName());
        
        if (table.getComment() != null && !table.getComment().trim().isEmpty()) {
            description.append(" - ").append(table.getComment());
        }
        
        description.append(". Columns: ");
        
        for (int i = 0; i < table.getColumns().size(); i++) {
            ColumnInfo column = table.getColumns().get(i);
            if (i > 0) description.append(", ");
            
            description.append(column.getColumnName())
                      .append(" (").append(column.getDataType()).append(")");
            
            if (column.getComment() != null && !column.getComment().trim().isEmpty()) {
                description.append(" - ").append(column.getComment());
            }
        }
        
        // Add foreign key relationships
        if (!table.getForeignKeys().isEmpty()) {
            description.append(". Relationships: ");
            for (int i = 0; i < table.getForeignKeys().size(); i++) {
                ForeignKeyInfo fk = table.getForeignKeys().get(i);
                if (i > 0) description.append(", ");
                description.append(fk.getFkColumnName())
                          .append(" references ")
                          .append(fk.getPkTableName())
                          .append(".")
                          .append(fk.getPkColumnName());
            }
        }
        
        return description.toString();
    }
    
    /**
     * Store embeddings in Qdrant vector database
     */
    private void storeEmbeddings(List<SchemaEmbedding> embeddings) throws ExecutionException, InterruptedException {
        if (embeddings.isEmpty()) return;
        
        List<PointStruct> points = new ArrayList<>();
        
        for (SchemaEmbedding embedding : embeddings) {
            Map<String, JsonWithInt.Value> payload = new HashMap<>();
            payload.put("connectionId", JsonWithInt.Value.newBuilder().setStringValue(embedding.getConnectionId()).build());
            payload.put("userId", JsonWithInt.Value.newBuilder().setStringValue(embedding.getUserId()).build());
            payload.put("tableName", JsonWithInt.Value.newBuilder().setStringValue(embedding.getTableName()).build());
            payload.put("description", JsonWithInt.Value.newBuilder().setStringValue(embedding.getDescription()).build());
            payload.put("timestamp", JsonWithInt.Value.newBuilder().setStringValue(embedding.getTimestamp().toString()).build());
            
            // Convert Float list to float array for vectors
            List<Float> vectorList = embedding.getVector();
            Vectors vectors = Vectors.newBuilder()
                .setVector(io.qdrant.client.grpc.Points.Vector.newBuilder().addAllData(vectorList).build())
                .build();
            
            PointStruct point = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setUuid(embedding.getId()).build())
                .setVectors(vectors)
                .putAllPayload(payload)
                .build();
            
            points.add(point);
        }
        
        UpsertPoints upsertRequest = UpsertPoints.newBuilder()
            .setCollectionName(collectionName)
            .addAllPoints(points)
            .build();
        
        ListenableFuture<UpdateResult> upsertFuture = qdrantClient.upsertAsync(upsertRequest);
        upsertFuture.get();
        
        logger.info("Stored {} embeddings in Qdrant collection: {}", embeddings.size(), collectionName);
    }
    
    /**
     * Ensure Qdrant collection exists with proper configuration
     */
    private void ensureCollectionExists() {
        try {
            // Check if collection exists
            ListenableFuture<io.qdrant.client.grpc.Collections.CollectionInfo> collectionInfoFuture = qdrantClient.getCollectionInfoAsync(collectionName);
            collectionInfoFuture.get(); // Just check if it exists, don't need the response
            
            logger.info("Qdrant collection '{}' already exists", collectionName);
            
        } catch (Exception e) {
            // Collection doesn't exist, create it
            try {
                CreateCollection createRequest = CreateCollection.newBuilder()
                    .setCollectionName(collectionName)
                    .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParams(VectorParams.newBuilder()
                            .setSize(384) // AllMiniLmL6V2 embedding dimension
                            .setDistance(Distance.Cosine)
                            .build())
                        .build())
                    .build();
                
                ListenableFuture<io.qdrant.client.grpc.Collections.CollectionOperationResponse> createFuture = qdrantClient.createCollectionAsync(createRequest);
                createFuture.get();
                
                logger.info("Created Qdrant collection: {}", collectionName);
                
            } catch (Exception createException) {
                logger.error("Failed to create Qdrant collection: {}", createException.getMessage(), createException);
            }
        }
    }
    
    /**
     * Helper method to extract string value from JsonWithInt.Value
     */
    private String getStringFromValue(JsonWithInt.Value value) {
        if (value == null) {
            return "";
        }
        return value.getStringValue();
    }
    
    // Data Classes
    
    /**
     * Schema analysis result
     */
    public static class SchemaAnalysisResult {
        private final String connectionId;
        private final String userId;
        private final DatabaseType databaseType;
        private final SchemaInfo schemaInfo;
        private final int embeddingsGenerated;
        private final boolean success;
        private final String error;
        private final long processingTimeMs;
        
        public SchemaAnalysisResult(String connectionId, String userId, DatabaseType databaseType,
                                  SchemaInfo schemaInfo, int embeddingsGenerated, boolean success,
                                  String error, long processingTimeMs) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.databaseType = databaseType;
            this.schemaInfo = schemaInfo;
            this.embeddingsGenerated = embeddingsGenerated;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
        }
        
        // Getters
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public SchemaInfo getSchemaInfo() { return schemaInfo; }
        public int getEmbeddingsGenerated() { return embeddingsGenerated; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }
    
    /**
     * Schema information container
     */
    public static class SchemaInfo {
        private final DatabaseType databaseType;
        private final List<TableInfo> tables;
        
        public SchemaInfo(DatabaseType databaseType, List<TableInfo> tables) {
            this.databaseType = databaseType;
            this.tables = tables;
        }
        
        public DatabaseType getDatabaseType() { return databaseType; }
        public List<TableInfo> getTables() { return tables; }
    }
    
    /**
     * Table information
     */
    public static class TableInfo {
        private final String tableName;
        private final String comment;
        private final List<ColumnInfo> columns;
        private final List<ForeignKeyInfo> foreignKeys;
        
        public TableInfo(String tableName, String comment, List<ColumnInfo> columns, List<ForeignKeyInfo> foreignKeys) {
            this.tableName = tableName;
            this.comment = comment;
            this.columns = columns;
            this.foreignKeys = foreignKeys;
        }
        
        public String getTableName() { return tableName; }
        public String getComment() { return comment; }
        public List<ColumnInfo> getColumns() { return columns; }
        public List<ForeignKeyInfo> getForeignKeys() { return foreignKeys; }
    }
    
    /**
     * Column information
     */
    public static class ColumnInfo {
        private final String columnName;
        private final String dataType;
        private final int columnSize;
        private final boolean nullable;
        private final String defaultValue;
        private final String comment;
        
        public ColumnInfo(String columnName, String dataType, int columnSize, boolean nullable, String defaultValue, String comment) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.columnSize = columnSize;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.comment = comment;
        }
        
        public String getColumnName() { return columnName; }
        public String getDataType() { return dataType; }
        public int getColumnSize() { return columnSize; }
        public boolean isNullable() { return nullable; }
        public String getDefaultValue() { return defaultValue; }
        public String getComment() { return comment; }
    }
    
    /**
     * Foreign key information
     */
    public static class ForeignKeyInfo {
        private final String fkColumnName;
        private final String pkTableName;
        private final String pkColumnName;
        
        public ForeignKeyInfo(String fkColumnName, String pkTableName, String pkColumnName) {
            this.fkColumnName = fkColumnName;
            this.pkTableName = pkTableName;
            this.pkColumnName = pkColumnName;
        }
        
        public String getFkColumnName() { return fkColumnName; }
        public String getPkTableName() { return pkTableName; }
        public String getPkColumnName() { return pkColumnName; }
    }
    
    /**
     * Schema embedding for vector storage
     */
    public static class SchemaEmbedding {
        private final String id;
        private final String connectionId;
        private final String userId;
        private final String tableName;
        private final String description;
        private final List<Float> vector;
        private final LocalDateTime timestamp;
        
        public SchemaEmbedding(String id, String connectionId, String userId, String tableName,
                             String description, List<Float> vector, LocalDateTime timestamp) {
            this.id = id;
            this.connectionId = connectionId;
            this.userId = userId;
            this.tableName = tableName;
            this.description = description;
            this.vector = vector;
            this.timestamp = timestamp;
        }
        
        public String getId() { return id; }
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public List<Float> getVector() { return vector; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    /**
     * Similar schema search result
     */
    public static class SimilarSchema {
        private final String connectionId;
        private final String userId;
        private final String tableName;
        private final String description;
        private final float similarity;
        
        public SimilarSchema(String connectionId, String userId, String tableName, String description, float similarity) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.tableName = tableName;
            this.description = description;
            this.similarity = similarity;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public float getSimilarity() { return similarity; }
    }
    
    /**
     * Schema agent statistics
     */
    public static class SchemaAgentStats {
        private final long totalSchemasAnalyzed;
        private final long totalTablesProcessed;
        private final long totalEmbeddingsGenerated;
        private final boolean qdrantConnected;
        
        public SchemaAgentStats(long totalSchemasAnalyzed, long totalTablesProcessed, 
                               long totalEmbeddingsGenerated, boolean qdrantConnected) {
            this.totalSchemasAnalyzed = totalSchemasAnalyzed;
            this.totalTablesProcessed = totalTablesProcessed;
            this.totalEmbeddingsGenerated = totalEmbeddingsGenerated;
            this.qdrantConnected = qdrantConnected;
        }
        
        public long getTotalSchemasAnalyzed() { return totalSchemasAnalyzed; }
        public long getTotalTablesProcessed() { return totalTablesProcessed; }
        public long getTotalEmbeddingsGenerated() { return totalEmbeddingsGenerated; }
        public boolean isQdrantConnected() { return qdrantConnected; }
    }
}

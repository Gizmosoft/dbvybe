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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.LocalDateTime;
import com.dbVybe.app.service.UserDatabaseConnectionService;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import com.dbVybe.app.service.ActorServiceLocator;

/**
 * Database Schema Agent - Analyzes database schemas and creates vector embeddings
 * 
 * This agent:
 * - Connects to user databases and analyzes their schema structure
 * - Generates vector embeddings for tables, columns, and relationships
 * - Stores embeddings in Qdrant vector database for semantic search
 * - Stores relationships in Neo4j graph database for relationship analysis
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
    
    // Graph Analysis Agent for Neo4j integration
    @Autowired
    private GraphAnalysisAgent graphAnalysisAgent;
    
    // Schema Analysis Statistics
    private long totalSchemasAnalyzed = 0;
    private long totalTablesProcessed = 0;
    private long totalEmbeddingsGenerated = 0;
    private long totalRelationshipsStored = 0;
    
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing Database Schema Agent with Qdrant integration");
            
            // Initialize embedding model (local model for better performance)
            this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            
            // Initialize Qdrant client
            if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
                try {
                    this.qdrantClient = new QdrantClient(
                        QdrantGrpcClient.newBuilder(qdrantUrl, 6334, true)
                            .withApiKey(qdrantApiKey)
                            .build()
                    );
                    
                    // Ensure collection exists
                    ensureCollectionExists();
                    logger.info("Qdrant client initialized successfully with host: {}", qdrantUrl);
                } catch (Exception e) {
                    logger.warn("Failed to initialize Qdrant client: {}. Schema analysis will work but embeddings won't be stored.", e.getMessage());
                    this.qdrantClient = null;
                }
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
                
                SchemaInfo schemaInfo;
                
                // Handle different database types
                if (databaseType == DatabaseType.MONGODB) {
                    // For MongoDB, we need to analyze collections instead of tables
                    schemaInfo = extractMongoDBSchemaInfo(connectionId, userId);
                } else {
                    // For SQL databases (PostgreSQL, MySQL), use existing JDBC-based analysis
                    schemaInfo = extractSchemaInfo(connection, databaseType);
                }
                
                // Generate embeddings for schema components
                List<SchemaEmbedding> embeddings = generateSchemaEmbeddings(schemaInfo, connectionId, userId);
                
                // Store embeddings in Qdrant
                if (qdrantClient != null && !embeddings.isEmpty()) {
                    storeEmbeddings(embeddings);
                }
                
                // Store relationships in Neo4j
                if (graphAnalysisAgent != null) {
                    List<GraphAnalysisAgent.TableRelationship> relationships = convertSchemaToRelationships(schemaInfo);
                    CompletableFuture<GraphAnalysisAgent.GraphStorageResult> graphResult = 
                        graphAnalysisAgent.storeSchemaRelationships(connectionId, userId, databaseType, relationships);
                    
                    try {
                        GraphAnalysisAgent.GraphStorageResult result = graphResult.get();
                        if (result.isSuccess()) {
                            totalRelationshipsStored += result.getRelationshipsStored();
                            logger.info("Successfully stored {} relationships in Neo4j for connection {}", 
                                result.getRelationshipsStored(), connectionId);
                        } else {
                            logger.warn("Failed to store relationships in Neo4j for connection {}: {}", 
                                connectionId, result.getError());
                        }
                    } catch (Exception e) {
                        logger.warn("Error storing relationships in Neo4j for connection {}: {}", connectionId, e.getMessage());
                    }
                }
                
                // Update statistics
                totalSchemasAnalyzed++;
                totalTablesProcessed += schemaInfo.getTables().size();
                totalEmbeddingsGenerated += embeddings.size();
                
                long processingTime = System.currentTimeMillis() - startTime;
                
                logger.info("Schema analysis completed for connection {} in {}ms. Tables: {}, Embeddings: {}, Relationships: {}", 
                    connectionId, processingTime, schemaInfo.getTables().size(), embeddings.size(), totalRelationshipsStored);
                
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
                logger.error("Schema analysis failed for connection {}: {}", connectionId, e.getMessage(), e);
                
                return new SchemaAnalysisResult(
                    connectionId,
                    userId,
                    databaseType,
                    null,
                    0,
                    false,
                    e.getMessage(),
                    System.currentTimeMillis() - startTime
                );
            }
        });
    }
    
    /**
     * Search for similar schemas using vector similarity
     */
    public CompletableFuture<List<SimilarSchema>> findSimilarSchemas(String query, int limit) {
        return findSimilarSchemas(query, limit, null);
    }
    
    /**
     * Search for similar schemas using vector similarity with connection filter
     */
    public CompletableFuture<List<SimilarSchema>> findSimilarSchemas(String query, int limit, String connectionId) {
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
                    .setLimit(limit * 2) // Get more results to filter by connectionId
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();
                
                ListenableFuture<List<ScoredPoint>> searchFuture = qdrantClient.searchAsync(searchRequest);
                List<ScoredPoint> searchResults = searchFuture.get();
                
                List<SimilarSchema> results = new ArrayList<>();
                for (ScoredPoint point : searchResults) {
                    @SuppressWarnings("deprecation")
                    Map<String, JsonWithInt.Value> payload = point.getPayload();
                    
                    String pointConnectionId = getStringFromValue(payload.get("connectionId"));
                    
                    // Filter by connectionId if specified
                    if (connectionId != null && !connectionId.equals(pointConnectionId)) {
                        continue;
                    }
                    
                    SimilarSchema schema = new SimilarSchema(
                        pointConnectionId,
                        getStringFromValue(payload.get("userId")),
                        getStringFromValue(payload.get("tableName")),
                        getStringFromValue(payload.get("description")),
                        point.getScore()
                    );
                    results.add(schema);
                    
                    // Stop if we have enough results
                    if (results.size() >= limit) {
                        break;
                    }
                }
                
                logger.info("Found {} similar schemas for query: {} (connectionId: {})", 
                    results.size(), query, connectionId);
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
                boolean qdrantDeleted = true;
                boolean neo4jDeleted = true;
                
                // Delete from Qdrant
                if (qdrantClient != null) {
                    logger.info("Deleting schema embeddings from Qdrant for connection {} (user: {})", connectionId, userId);
                    
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
                        
                        logger.info("Successfully deleted {} schema embeddings from Qdrant for connection {} (user: {})", 
                            pointsToDelete.size(), connectionId, userId);
                    } else {
                        logger.info("No schema embeddings found in Qdrant for connection {} (user: {})", connectionId, userId);
                    }
                } else {
                    logger.warn("Qdrant client not available. Cannot delete schema embeddings for connection: {}", connectionId);
                    qdrantDeleted = false;
                }
                
                // Delete from Neo4j
                if (graphAnalysisAgent != null) {
                    logger.info("Deleting graph relationships from Neo4j for connection {} (user: {})", connectionId, userId);
                    
                    try {
                        CompletableFuture<Boolean> neo4jResult = graphAnalysisAgent.deleteConnectionGraphData(connectionId, userId);
                        neo4jDeleted = neo4jResult.get();
                        
                        if (neo4jDeleted) {
                            logger.info("Successfully deleted graph relationships from Neo4j for connection {} (user: {})", connectionId, userId);
                        } else {
                            logger.warn("Failed to delete graph relationships from Neo4j for connection {} (user: {})", connectionId, userId);
                        }
                    } catch (Exception e) {
                        logger.warn("Error deleting graph relationships from Neo4j for connection {} (user: {}): {}", 
                            connectionId, userId, e.getMessage());
                        neo4jDeleted = false;
                    }
                } else {
                    logger.warn("GraphAnalysisAgent not available. Cannot delete graph relationships for connection: {}", connectionId);
                    neo4jDeleted = false;
                }
                
                return qdrantDeleted && neo4jDeleted;
                
            } catch (Exception e) {
                logger.error("Error deleting schema data for connection {} (user: {}): {}", 
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
            totalRelationshipsStored,
            qdrantClient != null
        );
    }
    
    /**
     * Extract schema information from database connection
     */
    private SchemaInfo extractSchemaInfo(Connection connection, DatabaseType databaseType) throws SQLException {
        if (connection == null) {
            throw new SQLException("Database connection is null");
        }
        
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
            if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                logger.info("Qdrant collection '{}' not found, creating it...", collectionName);
            } else {
                logger.warn("Error checking Qdrant collection '{}': {}. Attempting to create...", collectionName, e.getMessage());
            }
            
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
                
                logger.info("✅ Successfully created Qdrant collection: {}", collectionName);
                
            } catch (Exception createException) {
                logger.error("❌ Failed to create Qdrant collection '{}': {}", collectionName, createException.getMessage(), createException);
            }
        }
    }
    
    /**
     * Convert SchemaInfo to GraphAnalysisAgent.TableRelationship list
     */
    private List<GraphAnalysisAgent.TableRelationship> convertSchemaToRelationships(SchemaInfo schemaInfo) {
        List<GraphAnalysisAgent.TableRelationship> relationships = new ArrayList<>();
        
        for (TableInfo table : schemaInfo.getTables()) {
            for (ForeignKeyInfo fk : table.getForeignKeys()) {
                // Find the target table info for better description
                String targetDescription = "";
                for (TableInfo targetTable : schemaInfo.getTables()) {
                    if (targetTable.getTableName().equals(fk.getPkTableName())) {
                        targetDescription = targetTable.getComment() != null ? targetTable.getComment() : "";
                        break;
                    }
                }
                
                GraphAnalysisAgent.TableRelationship relationship = new GraphAnalysisAgent.TableRelationship(
                    table.getTableName(),                    // sourceTable
                    fk.getPkTableName(),                     // targetTable
                    fk.getFkColumnName(),                    // sourceColumn
                    fk.getPkColumnName(),                    // targetColumn
                    "FOREIGN_KEY",                           // relationshipType
                    table.getComment() != null ? table.getComment() : "",  // sourceDescription
                    targetDescription                        // targetDescription
                );
                
                relationships.add(relationship);
            }
        }
        
        logger.info("Converted {} foreign key relationships from schema", relationships.size());
        return relationships;
    }
    
    /**
     * Extract MongoDB schema information from collections
     */
    private SchemaInfo extractMongoDBSchemaInfo(String connectionId, String userId) {
        try {
            // Get connection details to connect to MongoDB
            UserDatabaseConnectionService connectionService = ActorServiceLocator.getUserDatabaseConnectionService();
            if (connectionService == null) {
                throw new RuntimeException("UserDatabaseConnectionService not available");
            }
            
            Optional<UserDatabaseConnection> connectionOpt = 
                connectionService.findUserDatabaseConnection(connectionId, userId);
            
            if (connectionOpt.isEmpty()) {
                throw new RuntimeException("Database connection not found: " + connectionId);
            }
            
            UserDatabaseConnection dbConnection = connectionOpt.get();
            
            // Create MongoDB connection
            String connectionString = buildMongoDBConnectionString(dbConnection);
            com.mongodb.client.MongoClient mongoClient = com.mongodb.client.MongoClients.create(connectionString);
            com.mongodb.client.MongoDatabase database = mongoClient.getDatabase(dbConnection.getDatabaseName());
            
            try {
                List<TableInfo> tables = new ArrayList<>();
                
                // Get all collections
                for (String collectionName : database.listCollectionNames()) {
                    com.mongodb.client.MongoCollection<org.bson.Document> collection = database.getCollection(collectionName);
                    
                    // Analyze collection structure by sampling documents
                    List<ColumnInfo> columns = analyzeMongoDBCollection(collection);
                    
                    // For MongoDB, we'll create "relationships" based on common field names and references
                    List<ForeignKeyInfo> foreignKeys = analyzeMongoDBRelationships(collection, collectionName);
                    
                    TableInfo tableInfo = new TableInfo(collectionName, "MongoDB Collection", columns, foreignKeys);
                    tables.add(tableInfo);
                }
                
                return new SchemaInfo(DatabaseType.MONGODB, tables);
                
            } finally {
                mongoClient.close();
            }
            
        } catch (Exception e) {
            logger.error("Failed to extract MongoDB schema for connection {}: {}", connectionId, e.getMessage(), e);
            throw new RuntimeException("Failed to extract MongoDB schema", e);
        }
    }
    
    /**
     * Analyze MongoDB collection structure by sampling documents
     */
    private List<ColumnInfo> analyzeMongoDBCollection(com.mongodb.client.MongoCollection<org.bson.Document> collection) {
        List<ColumnInfo> columns = new ArrayList<>();
        
        try {
            // Sample a few documents to understand the structure
            org.bson.Document sampleDoc = collection.find().first();
            if (sampleDoc != null) {
                analyzeDocumentFields(sampleDoc, "", columns);
            }
            
            // Also check for common MongoDB fields
            addCommonMongoDBFields(columns);
            
        } catch (Exception e) {
            logger.warn("Error analyzing MongoDB collection structure: {}", e.getMessage());
        }
        
        return columns;
    }
    
    /**
     * Recursively analyze document fields to extract column information
     */
    private void analyzeDocumentFields(org.bson.Document doc, String prefix, List<ColumnInfo> columns) {
        for (String key : doc.keySet()) {
            Object value = doc.get(key);
            String fieldName = prefix.isEmpty() ? key : prefix + "." + key;
            
            String dataType = getMongoDBDataType(value);
            String comment = "MongoDB field";
            
            ColumnInfo columnInfo = new ColumnInfo(
                fieldName, dataType, 0, true, null, comment
            );
            columns.add(columnInfo);
            
            // Recursively analyze nested documents
            if (value instanceof org.bson.Document) {
                analyzeDocumentFields((org.bson.Document) value, fieldName, columns);
            }
        }
    }
    
    /**
     * Get MongoDB data type from value
     */
    private String getMongoDBDataType(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "String";
        if (value instanceof Integer) return "Int32";
        if (value instanceof Long) return "Int64";
        if (value instanceof Double) return "Double";
        if (value instanceof Boolean) return "Boolean";
        if (value instanceof org.bson.Document) return "Document";
        if (value instanceof java.util.List) return "Array";
        if (value instanceof org.bson.types.ObjectId) return "ObjectId";
        if (value instanceof java.util.Date) return "Date";
        return value.getClass().getSimpleName();
    }
    
    /**
     * Add common MongoDB fields that are typically present
     */
    private void addCommonMongoDBFields(List<ColumnInfo> columns) {
        String[] commonFields = {"_id", "createdAt", "updatedAt", "deletedAt", "version"};
        String[] commonTypes = {"ObjectId", "Date", "Date", "Date", "Int32"};
        
        for (int i = 0; i < commonFields.length; i++) {
            final int index = i;
            boolean exists = columns.stream().anyMatch(col -> col.getColumnName().equals(commonFields[index]));
            if (!exists) {
                ColumnInfo columnInfo = new ColumnInfo(
                    commonFields[index], commonTypes[index], 0, true, null, "Common MongoDB field"
                );
                columns.add(columnInfo);
            }
        }
    }
    
    /**
     * Analyze MongoDB relationships based on common field patterns
     */
    private List<ForeignKeyInfo> analyzeMongoDBRelationships(com.mongodb.client.MongoCollection<org.bson.Document> collection, String collectionName) {
        List<ForeignKeyInfo> foreignKeys = new ArrayList<>();
        
        try {
            // Look for common reference patterns in MongoDB
            String[] referencePatterns = {"userId", "user_id", "customerId", "customer_id", "orderId", "order_id", 
                                        "productId", "product_id", "categoryId", "category_id"};
            
            for (String pattern : referencePatterns) {
                // Check if this collection has reference fields
                org.bson.Document query = new org.bson.Document(pattern, new org.bson.Document("$exists", true));
                long count = collection.countDocuments(query);
                
                if (count > 0) {
                    // This suggests a relationship to another collection
                    String targetCollection = pattern.replace("Id", "").replace("_id", "") + "s";
                    if (targetCollection.endsWith("s")) {
                        targetCollection = targetCollection.substring(0, targetCollection.length() - 1) + "s";
                    }
                    
                    ForeignKeyInfo fkInfo = new ForeignKeyInfo(pattern, targetCollection, "_id");
                    foreignKeys.add(fkInfo);
                }
            }
            
        } catch (Exception e) {
            logger.warn("Error analyzing MongoDB relationships: {}", e.getMessage());
        }
        
        return foreignKeys;
    }
    
    /**
     * Build MongoDB connection string from UserDatabaseConnection
     */
    private String buildMongoDBConnectionString(UserDatabaseConnection dbConnection) {
        StringBuilder connectionString = new StringBuilder();
        connectionString.append("mongodb://");
        
        if (dbConnection.getUsername() != null && !dbConnection.getUsername().isEmpty()) {
            connectionString.append(dbConnection.getUsername());
            if (dbConnection.getPassword() != null && !dbConnection.getPassword().isEmpty()) {
                connectionString.append(":").append(dbConnection.getPassword());
            }
            connectionString.append("@");
        }
        
        connectionString.append(dbConnection.getHost())
                       .append(":")
                       .append(dbConnection.getPort());
        
        // Add additional properties
        if (dbConnection.getAdditionalProperties() != null && !dbConnection.getAdditionalProperties().isEmpty()) {
            connectionString.append("/?");
            dbConnection.getAdditionalProperties().forEach((key, value) -> 
                connectionString.append(key).append("=").append(value).append("&"));
            connectionString.setLength(connectionString.length() - 1); // Remove last &
        }
        
        return connectionString.toString();
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
        private final long totalRelationshipsStored;
        private final boolean qdrantConnected;
        
        public SchemaAgentStats(long totalSchemasAnalyzed, long totalTablesProcessed, 
                               long totalEmbeddingsGenerated, long totalRelationshipsStored, boolean qdrantConnected) {
            this.totalSchemasAnalyzed = totalSchemasAnalyzed;
            this.totalTablesProcessed = totalTablesProcessed;
            this.totalEmbeddingsGenerated = totalEmbeddingsGenerated;
            this.totalRelationshipsStored = totalRelationshipsStored;
            this.qdrantConnected = qdrantConnected;
        }
        
        public long getTotalSchemasAnalyzed() { return totalSchemasAnalyzed; }
        public long getTotalTablesProcessed() { return totalTablesProcessed; }
        public long getTotalEmbeddingsGenerated() { return totalEmbeddingsGenerated; }
        public long getTotalRelationshipsStored() { return totalRelationshipsStored; }
        public boolean isQdrantConnected() { return qdrantConnected; }
    }
}

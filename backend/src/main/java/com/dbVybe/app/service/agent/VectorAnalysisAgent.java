package com.dbVybe.app.service.agent;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Vector Analysis Agent - Performs semantic search and analysis over vectorized schema data
 * 
 * This agent:
 * - Performs semantic search across database schemas using vector similarity
 * - Analyzes relationships between tables based on semantic similarity
 * - Provides contextual schema information for query generation
 * - Supports multi-database schema comparison and analysis
 */
@Service
public class VectorAnalysisAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorAnalysisAgent.class);
    
    // Qdrant Configuration
    @Value("${qdrant.url:https://your-cluster-url.qdrant.io:6334}")
    private String qdrantUrl;
    
    @Value("${qdrant.api-key}")
    private String qdrantApiKey;
    
    @Value("${qdrant.collection.name:dbvybe_schemas}")
    private String collectionName;
    
    // Vector Analysis Configuration
    private EmbeddingModel embeddingModel;
    private QdrantClient qdrantClient;
    
    // Analysis Statistics
    private long totalVectorSearches = 0;
    private long totalSemanticAnalyses = 0;
    private long totalContextQueries = 0;
    
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing Vector Analysis Agent with Qdrant integration");
            
            // Initialize embedding model
            this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            
            // Initialize Qdrant client
            if (qdrantApiKey != null && !qdrantApiKey.trim().isEmpty()) {
                this.qdrantClient = new QdrantClient(
                    QdrantGrpcClient.newBuilder(qdrantUrl, 6334, true)
                        .withApiKey(qdrantApiKey)
                        .build()
                );
                
                logger.info("Vector Analysis Agent initialized with Qdrant connection");
            } else {
                logger.warn("Qdrant API key not configured. Vector analysis will be limited.");
            }
            
        } catch (Exception e) {
            logger.error("Failed to initialize Vector Analysis Agent: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Perform semantic search for tables/schemas related to a query
     */
    public CompletableFuture<VectorSearchResult> semanticSchemaSearch(String query, String userId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            totalVectorSearches++;
            
            try {
                if (qdrantClient == null) {
                    return new VectorSearchResult(Collections.emptyList(), false, "Qdrant client not available", 0);
                }
                
                logger.info("Performing semantic schema search for query: '{}' (user: {}, limit: {})", 
                    query, userId, limit);
                
                // Generate embedding for the query
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                
                // Build search request with user filter
                SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryEmbedding.vectorAsList())
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());
                
                // Add user filter if specified
                if (userId != null && !userId.equals("anonymous")) {
                    // Note: In a real implementation, you might want to filter by user
                    // For now, we'll search across all schemas but could add user-specific filtering
                }
                
                SearchPoints searchRequest = searchBuilder.build();
                
                // Execute search
                ListenableFuture<List<ScoredPoint>> searchFuture = qdrantClient.searchAsync(searchRequest);
                List<ScoredPoint> searchResults = searchFuture.get();
                
                // Convert results to semantic matches
                List<SemanticMatch> matches = new ArrayList<>();
                for (ScoredPoint point : searchResults) {
                    @SuppressWarnings("deprecation")
                    Map<String, JsonWithInt.Value> payload = point.getPayload();
                    
                    SemanticMatch match = new SemanticMatch(
                        getStringFromValue(payload.get("connectionId")),
                        getStringFromValue(payload.get("userId")),
                        getStringFromValue(payload.get("tableName")),
                        getStringFromValue(payload.get("description")),
                        point.getScore(),
                        extractTableContext(getStringFromValue(payload.get("description")))
                    );
                    matches.add(match);
                }
                
                logger.info("Found {} semantic matches for query: '{}'", matches.size(), query);
                
                return new VectorSearchResult(matches, true, null, matches.size());
                
            } catch (Exception e) {
                logger.error("Error in semantic schema search: {}", e.getMessage(), e);
                return new VectorSearchResult(Collections.emptyList(), false, e.getMessage(), 0);
            }
        });
    }
    
    /**
     * Find related tables based on semantic similarity
     */
    public CompletableFuture<RelatedTablesResult> findRelatedTables(String tableName, String connectionId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            totalSemanticAnalyses++;
            
            try {
                if (qdrantClient == null) {
                    return new RelatedTablesResult(Collections.emptyList(), false, "Qdrant client not available");
                }
                
                logger.info("Finding related tables for: {} in connection: {}", tableName, connectionId);
                
                // First, get the vector for the source table
                String searchQuery = "table " + tableName;
                Embedding queryEmbedding = embeddingModel.embed(searchQuery).content();
                
                // Search for similar tables
                SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryEmbedding.vectorAsList())
                    .setLimit(limit + 1) // +1 to account for the source table itself
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();
                
                ListenableFuture<List<ScoredPoint>> searchFuture = qdrantClient.searchAsync(searchRequest);
                List<ScoredPoint> searchResults = searchFuture.get();
                
                // Convert to related table information
                List<RelatedTable> relatedTables = new ArrayList<>();
                for (ScoredPoint point : searchResults) {
                    @SuppressWarnings("deprecation")
                    Map<String, JsonWithInt.Value> payload = point.getPayload();
                    
                    String foundTableName = getStringFromValue(payload.get("tableName"));
                    String foundConnectionId = getStringFromValue(payload.get("connectionId"));
                    
                    // Skip the source table itself
                    if (foundTableName.equals(tableName) && foundConnectionId.equals(connectionId)) {
                        continue;
                    }
                    
                    RelatedTable relatedTable = new RelatedTable(
                        foundTableName,
                        foundConnectionId,
                        getStringFromValue(payload.get("userId")),
                        getStringFromValue(payload.get("description")),
                        point.getScore(),
                        determineRelationshipType(tableName, foundTableName, point.getScore())
                    );
                    
                    relatedTables.add(relatedTable);
                    
                    if (relatedTables.size() >= limit) {
                        break;
                    }
                }
                
                logger.info("Found {} related tables for: {}", relatedTables.size(), tableName);
                
                return new RelatedTablesResult(relatedTables, true, null);
                
            } catch (Exception e) {
                logger.error("Error finding related tables: {}", e.getMessage(), e);
                return new RelatedTablesResult(Collections.emptyList(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Get contextual information for query generation
     */
    public CompletableFuture<QueryContextResult> getQueryContext(String naturalLanguageQuery, String connectionId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            totalContextQueries++;
            
            try {
                logger.info("Getting query context for: '{}' (connection: {}, user: {})", 
                    naturalLanguageQuery, connectionId, userId);
                
                // Perform semantic search to find relevant tables
                VectorSearchResult searchResult = semanticSchemaSearch(naturalLanguageQuery, userId, 10).get();
                
                if (!searchResult.isSuccess()) {
                    return new QueryContextResult(Collections.emptyList(), Collections.emptyList(), false, searchResult.getError());
                }
                
                // Filter results for the specific connection if provided
                List<SemanticMatch> relevantMatches = searchResult.getMatches().stream()
                    .filter(match -> connectionId == null || connectionId.equals(match.getConnectionId()))
                    .collect(Collectors.toList());
                
                // Extract table contexts and relationships
                List<TableContext> tableContexts = new ArrayList<>();
                List<String> suggestedTables = new ArrayList<>();
                
                for (SemanticMatch match : relevantMatches) {
                    TableContext context = new TableContext(
                        match.getTableName(),
                        match.getDescription(),
                        match.getTableContext(),
                        match.getSimilarityScore()
                    );
                    tableContexts.add(context);
                    suggestedTables.add(match.getTableName());
                }
                
                logger.info("Generated query context with {} table contexts for query: '{}'", 
                    tableContexts.size(), naturalLanguageQuery);
                
                return new QueryContextResult(tableContexts, suggestedTables, true, null);
                
            } catch (Exception e) {
                logger.error("Error getting query context: {}", e.getMessage(), e);
                return new QueryContextResult(Collections.emptyList(), Collections.emptyList(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Analyze schema patterns across multiple databases
     */
    public CompletableFuture<SchemaPatternResult> analyzeSchemaPatterns(String userId, List<String> connectionIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Analyzing schema patterns for user: {} across {} connections", userId, connectionIds.size());
                
                // This would involve complex analysis across multiple schemas
                // For now, we'll provide a basic implementation
                
                Map<String, List<String>> patterns = new HashMap<>();
                patterns.put("common_table_patterns", Arrays.asList("users", "user_profiles", "authentication"));
                patterns.put("relationship_patterns", Arrays.asList("user_id references", "foreign key relationships"));
                
                return new SchemaPatternResult(patterns, true, null);
                
            } catch (Exception e) {
                logger.error("Error analyzing schema patterns: {}", e.getMessage(), e);
                return new SchemaPatternResult(Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Get vector analysis statistics
     */
    public VectorAnalysisStats getStats() {
        return new VectorAnalysisStats(
            totalVectorSearches,
            totalSemanticAnalyses,
            totalContextQueries,
            qdrantClient != null
        );
    }
    
    // Helper methods
    
    private String getStringFromValue(JsonWithInt.Value value) {
        if (value == null) {
            return "";
        }
        return value.getStringValue();
    }
    
    private Map<String, String> extractTableContext(String description) {
        Map<String, String> context = new HashMap<>();
        
        // Extract table name
        if (description.contains("Table: ")) {
            String tableName = description.substring(description.indexOf("Table: ") + 7);
            if (tableName.contains(" ")) {
                tableName = tableName.substring(0, tableName.indexOf(" "));
            }
            context.put("table_name", tableName);
        }
        
        // Extract column information
        if (description.contains("Columns: ")) {
            String columnsSection = description.substring(description.indexOf("Columns: ") + 9);
            if (columnsSection.contains(".")) {
                columnsSection = columnsSection.substring(0, columnsSection.indexOf("."));
            }
            context.put("columns", columnsSection);
        }
        
        // Extract relationships
        if (description.contains("Relationships: ")) {
            String relationshipsSection = description.substring(description.indexOf("Relationships: ") + 15);
            context.put("relationships", relationshipsSection);
        }
        
        return context;
    }
    
    private String determineRelationshipType(String sourceTable, String targetTable, float similarity) {
        if (similarity > 0.9f) {
            return "HIGHLY_RELATED";
        } else if (similarity > 0.7f) {
            return "SEMANTICALLY_SIMILAR";
        } else if (similarity > 0.5f) {
            return "POTENTIALLY_RELATED";
        } else {
            return "WEAKLY_RELATED";
        }
    }
    
    // Data Classes
    
    public static class VectorSearchResult {
        private final List<SemanticMatch> matches;
        private final boolean success;
        private final String error;
        private final int totalResults;
        
        public VectorSearchResult(List<SemanticMatch> matches, boolean success, String error, int totalResults) {
            this.matches = matches;
            this.success = success;
            this.error = error;
            this.totalResults = totalResults;
        }
        
        public List<SemanticMatch> getMatches() { return matches; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public int getTotalResults() { return totalResults; }
    }
    
    public static class SemanticMatch {
        private final String connectionId;
        private final String userId;
        private final String tableName;
        private final String description;
        private final float similarityScore;
        private final Map<String, String> tableContext;
        
        public SemanticMatch(String connectionId, String userId, String tableName, String description, 
                           float similarityScore, Map<String, String> tableContext) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.tableName = tableName;
            this.description = description;
            this.similarityScore = similarityScore;
            this.tableContext = tableContext;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public float getSimilarityScore() { return similarityScore; }
        public Map<String, String> getTableContext() { return tableContext; }
    }
    
    public static class RelatedTablesResult {
        private final List<RelatedTable> relatedTables;
        private final boolean success;
        private final String error;
        
        public RelatedTablesResult(List<RelatedTable> relatedTables, boolean success, String error) {
            this.relatedTables = relatedTables;
            this.success = success;
            this.error = error;
        }
        
        public List<RelatedTable> getRelatedTables() { return relatedTables; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class RelatedTable {
        private final String tableName;
        private final String connectionId;
        private final String userId;
        private final String description;
        private final float similarityScore;
        private final String relationshipType;
        
        public RelatedTable(String tableName, String connectionId, String userId, String description, 
                          float similarityScore, String relationshipType) {
            this.tableName = tableName;
            this.connectionId = connectionId;
            this.userId = userId;
            this.description = description;
            this.similarityScore = similarityScore;
            this.relationshipType = relationshipType;
        }
        
        public String getTableName() { return tableName; }
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public String getDescription() { return description; }
        public float getSimilarityScore() { return similarityScore; }
        public String getRelationshipType() { return relationshipType; }
    }
    
    public static class QueryContextResult {
        private final List<TableContext> tableContexts;
        private final List<String> suggestedTables;
        private final boolean success;
        private final String error;
        
        public QueryContextResult(List<TableContext> tableContexts, List<String> suggestedTables, 
                                boolean success, String error) {
            this.tableContexts = tableContexts;
            this.suggestedTables = suggestedTables;
            this.success = success;
            this.error = error;
        }
        
        public List<TableContext> getTableContexts() { return tableContexts; }
        public List<String> getSuggestedTables() { return suggestedTables; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class TableContext {
        private final String tableName;
        private final String description;
        private final Map<String, String> context;
        private final float relevanceScore;
        
        public TableContext(String tableName, String description, Map<String, String> context, float relevanceScore) {
            this.tableName = tableName;
            this.description = description;
            this.context = context;
            this.relevanceScore = relevanceScore;
        }
        
        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public Map<String, String> getContext() { return context; }
        public float getRelevanceScore() { return relevanceScore; }
    }
    
    public static class SchemaPatternResult {
        private final Map<String, List<String>> patterns;
        private final boolean success;
        private final String error;
        
        public SchemaPatternResult(Map<String, List<String>> patterns, boolean success, String error) {
            this.patterns = patterns;
            this.success = success;
            this.error = error;
        }
        
        public Map<String, List<String>> getPatterns() { return patterns; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class VectorAnalysisStats {
        private final long totalVectorSearches;
        private final long totalSemanticAnalyses;
        private final long totalContextQueries;
        private final boolean qdrantConnected;
        
        public VectorAnalysisStats(long totalVectorSearches, long totalSemanticAnalyses, 
                                 long totalContextQueries, boolean qdrantConnected) {
            this.totalVectorSearches = totalVectorSearches;
            this.totalSemanticAnalyses = totalSemanticAnalyses;
            this.totalContextQueries = totalContextQueries;
            this.qdrantConnected = qdrantConnected;
        }
        
        public long getTotalVectorSearches() { return totalVectorSearches; }
        public long getTotalSemanticAnalyses() { return totalSemanticAnalyses; }
        public long getTotalContextQueries() { return totalContextQueries; }
        public boolean isQdrantConnected() { return qdrantConnected; }
    }
}

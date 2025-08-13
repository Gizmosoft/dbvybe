package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.cluster.ClusterManager;
import com.dbVybe.app.domain.dto.LLMChatRequest;
import com.dbVybe.app.actor.analysis.VectorizationActor;
import com.dbVybe.app.actor.analysis.GraphActor;
import com.dbVybe.app.actor.llm.QueryExecutorActor;
import com.dbVybe.app.service.UserDatabaseConnectionService;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.service.agent.NLPAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Enhanced Chat Controller with full database context integration
 * 
 * Features:
 * - Database-aware chat sessions
 * - Automatic schema context retrieval
 * - Vector similarity search for relevant tables
 * - Graph relationship analysis
 * - Intelligent query generation and execution
 * - Safety checks for dangerous operations
 * - Tabular data presentation
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*", maxAge = 3600)
public class EnhancedChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedChatController.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(45); // Longer timeout for complex operations
    
    private final ClusterManager clusterManager;
    private final UserDatabaseConnectionService connectionService;
    private final NLPAgent nlpAgent;
    
    @Autowired
    public EnhancedChatController(ClusterManager clusterManager, 
                                 UserDatabaseConnectionService connectionService,
                                 NLPAgent nlpAgent) {
        this.clusterManager = clusterManager;
        this.connectionService = connectionService;
        this.nlpAgent = nlpAgent;
    }
    
    /**
     * Enhanced chat endpoint with full database context integration
     */
    @PostMapping("/database")
    public CompletionStage<ResponseEntity<EnhancedChatResponse>> chatWithDatabase(
            @RequestBody LLMChatRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Enhanced chat request from user {} for connection {}: {}", 
            finalUserId, request.getConnectionId(), request.getMessage());
        
        if (request.getConnectionId() == null || request.getConnectionId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(new EnhancedChatResponse(
                    null, "Connection ID is required for database chat", 
                    request.getSessionId(), true, null, null, null))
            );
        }
        
        return processEnhancedChat(request, finalUserId);
    }
    
    /**
     * Process enhanced chat with full context integration
     */
    private CompletionStage<ResponseEntity<EnhancedChatResponse>> processEnhancedChat(
            LLMChatRequest request, String userId) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Get database connection details
                Optional<UserDatabaseConnection> connectionOpt = 
                    connectionService.findUserDatabaseConnection(request.getConnectionId(), userId);
                
                if (connectionOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(new EnhancedChatResponse(
                        null, "Database connection not found or not accessible", 
                        request.getSessionId(), true, null, null, null));
                }
                
                UserDatabaseConnection dbConnection = connectionOpt.get();
                DatabaseType databaseType = DatabaseType.fromString(dbConnection.getDatabaseType());
                
                logger.info("Processing chat for {} database: {}", databaseType, dbConnection.getDatabaseName());
                
                // 2. Determine if this requires query generation
                CompletableFuture<Boolean> requiresQueryFuture = nlpAgent.requiresQueryGeneration(request.getMessage());
                boolean requiresQuery = requiresQueryFuture.get();
                
                if (!requiresQuery) {
                    // Handle as general chat
                    return handleGeneralChat(request, userId, databaseType, dbConnection);
                } else {
                    // Handle as analytical/query request
                    return handleAnalyticalChat(request, userId, databaseType, dbConnection);
                }
                
            } catch (Exception e) {
                logger.error("Error processing enhanced chat: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new EnhancedChatResponse(
                        null, "Internal server error: " + e.getMessage(), 
                        request.getSessionId(), true, null, null, null));
            }
        });
    }
    
    /**
     * Handle general chat questions
     */
    private ResponseEntity<EnhancedChatResponse> handleGeneralChat(
            LLMChatRequest request, String userId, DatabaseType databaseType, UserDatabaseConnection dbConnection) {
        
        try {
            logger.info("Handling general chat question for user {}", userId);
            
            CompletableFuture<NLPAgent.AgentResponse> responseFuture = 
                nlpAgent.processGeneralMessage(request.getMessage(), userId, request.getSessionId());
            
            NLPAgent.AgentResponse agentResponse = responseFuture.get();
            
            if (agentResponse.isSuccess()) {
                return ResponseEntity.ok(new EnhancedChatResponse(
                    null, agentResponse.getContent(), request.getSessionId(), 
                    false, "general", null, null));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new EnhancedChatResponse(
                        null, "Error processing general chat: " + agentResponse.getError(), 
                        request.getSessionId(), true, null, null, null));
            }
            
        } catch (Exception e) {
            logger.error("Error in general chat processing: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new EnhancedChatResponse(
                    null, "Error processing general chat: " + e.getMessage(), 
                    request.getSessionId(), true, null, null, null));
        }
    }
    
    /**
     * Handle analytical/query chat questions with full context integration
     */
    private ResponseEntity<EnhancedChatResponse> handleAnalyticalChat(
            LLMChatRequest request, String userId, DatabaseType databaseType, UserDatabaseConnection dbConnection) {
        
        try {
            logger.info("Handling analytical chat question for user {}", userId);
            
            // 1. Get vector context for relevant tables
            Map<String, Object> vectorContext = getVectorContext(request.getMessage(), request.getConnectionId(), userId);
            
            // 2. Get graph context for relationships
            Map<String, Object> graphContext = getGraphContext(request.getMessage(), request.getConnectionId());
            
            // 3. Combine contexts for schema information
            Map<String, Object> combinedSchema = new HashMap<>();
            combinedSchema.put("databaseType", databaseType.toString());
            combinedSchema.put("databaseName", dbConnection.getDatabaseName());
            combinedSchema.put("vectorContext", vectorContext);
            combinedSchema.put("graphContext", graphContext);
            
            // 4. Generate query with full context
            CompletableFuture<NLPAgent.QueryGenerationResponse> queryFuture = 
                nlpAgent.generateDatabaseQuery(request.getMessage(), userId, request.getSessionId(), databaseType, combinedSchema);
            
            NLPAgent.QueryGenerationResponse queryResponse = queryFuture.get();
            
            if (!queryResponse.isSuccess()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new EnhancedChatResponse(
                        null, "Error generating query: " + queryResponse.getError(), 
                        request.getSessionId(), true, null, null, null));
            }
            
            String generatedQuery = queryResponse.getGeneratedQuery();
            String explanation = queryResponse.getExplanation();
            
            logger.info("Generated query for user {}: {}", userId, generatedQuery);
            
            // 5. Check if query is safe to execute
            if (isQuerySafe(generatedQuery)) {
                // Execute the query and return results with data
                return executeQueryAndReturnResults(request, userId, generatedQuery, explanation, databaseType);
            } else {
                // Return query with safety warning
                return ResponseEntity.ok(new EnhancedChatResponse(
                    generatedQuery, 
                    "⚠️ **Query Generated but NOT Executed for Safety**\n\n" +
                    "The system detected that this query contains potentially dangerous operations (UPDATE, DELETE, DROP, etc.) " +
                    "and will not execute it automatically. Here's the query for your reference:\n\n" +
                    "```sql\n" + generatedQuery + "\n```\n\n" +
                    "**Explanation:** " + explanation + "\n\n" +
                    "If you need to run this query, please execute it manually in a safe environment.",
                    request.getSessionId(), false, "query_blocked", null, null));
            }
            
        } catch (Exception e) {
            logger.error("Error in analytical chat processing: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new EnhancedChatResponse(
                    null, "Error processing analytical chat: " + e.getMessage(), 
                    request.getSessionId(), true, null, null, null));
        }
    }
    
    /**
     * Execute query and return results in a user-friendly format
     */
    private ResponseEntity<EnhancedChatResponse> executeQueryAndReturnResults(
            LLMChatRequest request, String userId, String query, String explanation, DatabaseType databaseType) {
        
        try {
            logger.info("Executing query for user {}: {}", userId, query);
            
            // Execute query via QueryExecutorActor
            ActorRef<QueryExecutorActor.Command> queryExecutorActor = clusterManager.getQueryExecutorActor();
            Scheduler scheduler = clusterManager.getLLMProcessingScheduler();
            
            CompletionStage<QueryExecutorActor.QueryExecutionResponse> executionFuture = 
                AskPattern.ask(
                    queryExecutorActor,
                    replyTo -> new QueryExecutorActor.ExecuteQuery(
                        query, request.getConnectionId(), userId, 100, replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            QueryExecutorActor.QueryExecutionResponse executionResult = executionFuture.toCompletableFuture().get();
            
            if (executionResult.isSuccess() && executionResult.getData() != null) {
                // Format results as tabular data
                String formattedResponse = formatQueryResults(executionResult, explanation, query);
                
                return ResponseEntity.ok(new EnhancedChatResponse(
                    query, formattedResponse, request.getSessionId(), 
                    false, "query_executed", executionResult.getData(), 
                    Map.of(
                        "rowCount", executionResult.getRowCount(),
                        "executionTime", executionResult.getExecutionTimeMs(),
                        "databaseType", databaseType.toString()
                    )));
                    
            } else {
                // Query failed to execute
                return ResponseEntity.ok(new EnhancedChatResponse(
                    query, 
                    "❌ **Query Execution Failed**\n\n" +
                    "The generated query could not be executed successfully.\n\n" +
                    "**Error:** " + executionResult.getError() + "\n\n" +
                    "**Generated Query:**\n```sql\n" + query + "\n```\n\n" +
                    "**Explanation:** " + explanation,
                    request.getSessionId(), false, "query_failed", null, null));
            }
            
        } catch (Exception e) {
            logger.error("Error executing query: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new EnhancedChatResponse(
                    query, "Error executing query: " + e.getMessage(), 
                    request.getSessionId(), true, null, null, null));
        }
    }
    
    /**
     * Format query results for user-friendly display
     */
    private String formatQueryResults(QueryExecutorActor.QueryExecutionResponse result, String explanation, String query) {
        StringBuilder response = new StringBuilder();
        
        response.append("✅ **Query Executed Successfully**\n\n");
        response.append("**Results:** ").append(result.getRowCount()).append(" rows returned in ")
                .append(result.getExecutionTimeMs()).append("ms\n\n");
        
        // Add explanation
        if (explanation != null && !explanation.trim().isEmpty()) {
            response.append("**Query Explanation:** ").append(explanation).append("\n\n");
        }
        
        // Add tabular data representation
        if (result.getData() != null && !result.getData().getRows().isEmpty()) {
            response.append("**Data:**\n\n");
            response.append(formatTableData(result.getData()));
        } else {
            response.append("*No data returned by the query.*\n");
        }
        
        // Add executed query for reference
        response.append("\n**Executed Query:**\n```sql\n").append(query).append("\n```");
        
        return response.toString();
    }
    
    /**
     * Format table data as markdown table
     */
    private String formatTableData(com.dbVybe.app.service.agent.QueryExecutionAgent.QueryResultData data) {
        StringBuilder table = new StringBuilder();
        
        // Table header
        table.append("| ");
        for (String columnName : data.getColumnNames()) {
            table.append(columnName).append(" | ");
        }
        table.append("\n");
        
        // Table separator
        table.append("| ");
        for (int i = 0; i < data.getColumnNames().size(); i++) {
            table.append("--- | ");
        }
        table.append("\n");
        
        // Table rows (limit to first 50 rows for readability)
        int rowLimit = Math.min(data.getRows().size(), 50);
        for (int i = 0; i < rowLimit; i++) {
            List<Object> row = data.getRows().get(i);
            table.append("| ");
            for (Object value : row) {
                String cellValue = (value != null) ? value.toString() : "NULL";
                // Escape pipe characters in cell values
                cellValue = cellValue.replace("|", "\\|");
                table.append(cellValue).append(" | ");
            }
            table.append("\n");
        }
        
        if (data.getRows().size() > rowLimit) {
            table.append("\n*... and ").append(data.getRows().size() - rowLimit).append(" more rows*\n");
        }
        
        return table.toString();
    }
    
    /**
     * Get vector context for the query
     */
    private Map<String, Object> getVectorContext(String query, String connectionId, String userId) {
        try {
            ActorRef<VectorizationActor.Command> vectorActor = clusterManager.getVectorizationActor();
            if (vectorActor == null) {
                return Collections.emptyMap();
            }
            
            // This would be implemented to get vector context
            // For now, return empty map as placeholder
            logger.debug("Getting vector context for query: {}", query);
            return Map.of("relevantTables", Collections.emptyList());
            
        } catch (Exception e) {
            logger.warn("Error getting vector context: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    /**
     * Get graph context for the query
     */
    private Map<String, Object> getGraphContext(String query, String connectionId) {
        try {
            ActorRef<GraphActor.Command> graphActor = clusterManager.getGraphActor();
            if (graphActor == null) {
                return Collections.emptyMap();
            }
            
            // This would be implemented to get graph context
            // For now, return empty map as placeholder
            logger.debug("Getting graph context for query: {}", query);
            return Map.of("relationships", Collections.emptyList());
            
        } catch (Exception e) {
            logger.warn("Error getting graph context: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
    
    /**
     * Check if query is safe to execute (read-only operations)
     */
    private boolean isQuerySafe(String query) {
        if (query == null) return false;
        
        String upperQuery = query.toUpperCase().trim();
        
        // Allow only safe read operations
        return upperQuery.startsWith("SELECT") || 
               upperQuery.startsWith("SHOW") || 
               upperQuery.startsWith("DESCRIBE") || 
               upperQuery.startsWith("EXPLAIN") ||
               upperQuery.startsWith("WITH") ||
               (upperQuery.startsWith("{") && upperQuery.contains("\"find\"")) || // MongoDB find
               (upperQuery.startsWith("{") && upperQuery.contains("\"aggregate\"")) || // MongoDB aggregate
               (upperQuery.startsWith("{") && upperQuery.contains("\"count\"")) || // MongoDB count
               (upperQuery.startsWith("{") && upperQuery.contains("\"distinct\"")); // MongoDB distinct
    }
    
    /**
     * Enhanced chat response with additional metadata
     */
    public static class EnhancedChatResponse {
        private String query;
        private String response;
        private String sessionId;
        private boolean error;
        private String responseType; // "general", "query_executed", "query_blocked", "query_failed"
        private Object data; // Actual query result data
        private Map<String, Object> metadata; // Additional metadata
        
        public EnhancedChatResponse(String query, String response, String sessionId, boolean error, 
                                  String responseType, Object data, Map<String, Object> metadata) {
            this.query = query;
            this.response = response;
            this.sessionId = sessionId;
            this.error = error;
            this.responseType = responseType;
            this.data = data;
            this.metadata = metadata;
        }
        
        // Getters
        public String getQuery() { return query; }
        public String getResponse() { return response; }
        public String getSessionId() { return sessionId; }
        public boolean isError() { return error; }
        public String getResponseType() { return responseType; }
        public Object getData() { return data; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}

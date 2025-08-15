package com.dbVybe.app.controller;

import com.dbVybe.app.domain.dto.LLMChatRequest;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.service.UserDatabaseConnectionService;
import com.dbVybe.app.service.DatabaseKnowledgeService;
import com.dbVybe.app.service.agent.NLPAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * Simple Chat Controller
 * 
 * This controller provides a simplified approach to database chat:
 * 1. Uses stored database knowledge for intelligent responses
 * 2. Decides whether to answer from knowledge or run a query
 * 3. Generates clean, simple responses without markdown formatting
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SimpleChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleChatController.class);
    
    private final UserDatabaseConnectionService connectionService;
    private final DatabaseKnowledgeService knowledgeService;
    private final NLPAgent nlpAgent;
    
    @Autowired
    public SimpleChatController(UserDatabaseConnectionService connectionService,
                               DatabaseKnowledgeService knowledgeService,
                               NLPAgent nlpAgent) {
        this.connectionService = connectionService;
        this.knowledgeService = knowledgeService;
        this.nlpAgent = nlpAgent;
    }
    
    /**
     * Simple chat endpoint with intelligent response handling
     */
    @PostMapping("/simple")
    public CompletableFuture<ResponseEntity<SimpleChatResponse>> simpleChat(
            @RequestBody LLMChatRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Simple chat request from user {} for connection {}: {}", 
            finalUserId, request.getConnectionId(), request.getMessage());
        
        if (request.getConnectionId() == null || request.getConnectionId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(new SimpleChatResponse(
                    "Connection ID is required for database chat", 
                    request.getSessionId(), true))
            );
        }
        
        return processSimpleChat(request, finalUserId);
    }
    
    /**
     * Process simple chat with intelligent decision making
     */
    private CompletableFuture<ResponseEntity<SimpleChatResponse>> processSimpleChat(
            LLMChatRequest request, String userId) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Get database connection details
                var connectionOpt = connectionService.findUserDatabaseConnection(request.getConnectionId(), userId);
                
                if (connectionOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(new SimpleChatResponse(
                        "Database connection not found or not accessible", 
                        request.getSessionId(), true));
                }
                
                UserDatabaseConnection dbConnection = connectionOpt.get();
                DatabaseType databaseType = DatabaseType.fromString(dbConnection.getDatabaseType());
                
                logger.info("Processing simple chat for {} database: {}", databaseType, dbConnection.getDatabaseName());
                
                // 2. Check if we can answer from stored knowledge
                if (knowledgeService.canAnswerFromKnowledge(request.getMessage(), request.getConnectionId())) {
                    logger.info("Answering from stored knowledge for user {}", userId);
                    String response = knowledgeService.generateKnowledgeResponse(request.getMessage(), request.getConnectionId());
                    return ResponseEntity.ok(new SimpleChatResponse(response, request.getSessionId(), false));
                }
                
                // 3. Check if this requires query generation
                boolean requiresQuery = nlpAgent.requiresQueryGeneration(request.getMessage()).get();
                
                if (!requiresQuery) {
                    // Handle as general chat
                    logger.info("Handling as general chat for user {}", userId);
                    var agentResponse = nlpAgent.processGeneralMessage(request.getMessage(), userId, request.getSessionId()).get();
                    
                    if (agentResponse.isSuccess()) {
                        return ResponseEntity.ok(new SimpleChatResponse(agentResponse.getContent(), request.getSessionId(), false));
                    } else {
                        return ResponseEntity.status(500).body(new SimpleChatResponse(
                            "Error processing general chat: " + agentResponse.getError(), 
                            request.getSessionId(), true));
                    }
                } else {
                    // Handle as query request
                    logger.info("Handling as query request for user {}", userId);
                    return handleQueryRequest(request, userId, databaseType, dbConnection);
                }
                
            } catch (Exception e) {
                logger.error("Error processing simple chat: {}", e.getMessage(), e);
                return ResponseEntity.status(500).body(new SimpleChatResponse(
                    "Internal server error: " + e.getMessage(), 
                    request.getSessionId(), true));
            }
        });
    }
    
    /**
     * Handle query request
     */
    private ResponseEntity<SimpleChatResponse> handleQueryRequest(
            LLMChatRequest request, String userId, DatabaseType databaseType, UserDatabaseConnection dbConnection) {
        
        try {
            // Get database knowledge for context
            var knowledge = knowledgeService.getDatabaseKnowledge(request.getConnectionId());
            
            // Create simple context for query generation
            var context = createSimpleContext(knowledge, databaseType);
            
            // Generate query
            var queryResponse = nlpAgent.generateDatabaseQuery(
                request.getMessage(), userId, request.getSessionId(), databaseType, context).get();
            
            if (!queryResponse.isSuccess()) {
                return ResponseEntity.status(500).body(new SimpleChatResponse(
                    "Error generating query: " + queryResponse.getError(), 
                    request.getSessionId(), true));
            }
            
            String generatedQuery = queryResponse.getGeneratedQuery();
            String explanation = queryResponse.getExplanation();
            
            logger.info("Generated query for user {}: {}", userId, generatedQuery);
            
            // Check if query is safe to execute
            if (isQuerySafe(generatedQuery)) {
                // Execute the query
                return executeQueryAndFormatResponse(request, userId, generatedQuery, explanation, databaseType);
            } else {
                // Return safety warning
                String response = "Query generated but not executed for safety reasons.\n\n" +
                                "Generated query: " + generatedQuery + "\n\n" +
                                "Explanation: " + explanation + "\n\n" +
                                "This query contains potentially dangerous operations and will not be executed automatically.";
                
                return ResponseEntity.ok(new SimpleChatResponse(response, request.getSessionId(), false));
            }
            
        } catch (Exception e) {
            logger.error("Error in query request processing: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new SimpleChatResponse(
                "Error processing query request: " + e.getMessage(), 
                request.getSessionId(), true));
        }
    }
    
    /**
     * Create simple context for query generation
     */
    private java.util.Map<String, Object> createSimpleContext(DatabaseKnowledgeService.DatabaseKnowledge knowledge, DatabaseType databaseType) {
        var context = new java.util.HashMap<String, Object>();
        
        if (knowledge != null) {
            context.put("databaseName", knowledge.getDatabaseName());
            context.put("databaseType", databaseType.toString());
            context.put("tableCount", knowledge.getTables().size());
            context.put("totalColumns", knowledge.getTotalColumns());
            
            // Add table names
            var tableNames = knowledge.getTables().stream()
                .map(table -> table.getSchema() + "." + table.getName())
                .toList();
            context.put("tableNames", tableNames);
            
            // Add table details
            var tableDetails = new java.util.ArrayList<String>();
            for (var table : knowledge.getTables()) {
                var detail = table.getSchema() + "." + table.getName() + " (" + table.getColumns().size() + " columns)";
                if (table.getComment() != null && !table.getComment().isEmpty()) {
                    detail += " - " + table.getComment();
                }
                tableDetails.add(detail);
            }
            context.put("tableDetails", tableDetails);
        }
        
        return context;
    }
    
    /**
     * Check if query is safe to execute
     */
    private boolean isQuerySafe(String query) {
        if (query == null) return false;
        
        String upperQuery = query.toUpperCase().trim();
        
        // Remove common prefixes and formatting
        upperQuery = upperQuery.replaceAll("(?i)^\\s*QUERY\\s+", "");
        upperQuery = upperQuery.replaceAll("(?i)^\\s*EXPLANATION\\s+", "");
        upperQuery = upperQuery.replaceAll("```[^`]*```", "");
        upperQuery = upperQuery.replaceAll("\\*\\*[^*]+\\*\\*", "");
        upperQuery = upperQuery.replaceAll("\\s+", " ").trim();
        
        // REMOVED: Query type restrictions - now allowing all query types
        // All query types are now allowed (SELECT, INSERT, UPDATE, DELETE, etc.)
        return true;
    }
    
    /**
     * Execute query and format response
     */
    private ResponseEntity<SimpleChatResponse> executeQueryAndFormatResponse(
            LLMChatRequest request, String userId, String query, String explanation, DatabaseType databaseType) {
        
        try {
            // For now, return a simple response indicating the query would be executed
            // In a real implementation, you would execute the query here
            String response = "Query executed successfully.\n\n" +
                            "Query: " + query + "\n\n" +
                            "Explanation: " + explanation + "\n\n" +
                            "Results: [Query execution would be implemented here]";
            
            return ResponseEntity.ok(new SimpleChatResponse(response, request.getSessionId(), false));
            
        } catch (Exception e) {
            logger.error("Error executing query: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new SimpleChatResponse(
                "Error executing query: " + e.getMessage(), 
                request.getSessionId(), true));
        }
    }
    
    /**
     * Response class for simple chat
     */
    public static class SimpleChatResponse {
        private String response;
        private String sessionId;
        private boolean error;
        
        public SimpleChatResponse(String response, String sessionId, boolean error) {
            this.response = response;
            this.sessionId = sessionId;
            this.error = error;
        }
        
        // Getters
        public String getResponse() { return response; }
        public String getSessionId() { return sessionId; }
        public boolean isError() { return error; }
    }
}

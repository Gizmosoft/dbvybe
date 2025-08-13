package com.dbVybe.app.service.agent;

import com.dbVybe.app.domain.model.DatabaseType;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * Natural Language Processing Agent interface for handling user queries
 * This agent can process general chat messages and generate database queries
 */
public interface NLPAgent {
    
    /**
     * Process a general chat message that doesn't require database query generation
     * 
     * @param message The user's message
     * @param userId The user ID
     * @param sessionId The session ID
     * @return CompletableFuture containing the agent's response
     */
    CompletableFuture<AgentResponse> processGeneralMessage(String message, String userId, String sessionId);
    
    /**
     * Generate a database query from natural language
     * 
     * @param message The user's natural language query request
     * @param userId The user ID
     * @param sessionId The session ID
     * @param databaseType The type of database (MySQL, PostgreSQL, MongoDB)
     * @param databaseSchema Schema information for context
     * @return CompletableFuture containing the generated query and explanation
     */
    CompletableFuture<QueryGenerationResponse> generateDatabaseQuery(
        String message, 
        String userId, 
        String sessionId, 
        DatabaseType databaseType,
        Map<String, Object> databaseSchema
    );
    
    /**
     * Determine if a user message requires database query generation
     * 
     * @param message The user's message
     * @return CompletableFuture<Boolean> true if query generation is needed
     */
    CompletableFuture<Boolean> requiresQueryGeneration(String message);
    
    /**
     * Get agent status and health information
     * 
     * @return CompletableFuture containing agent status
     */
    CompletableFuture<AgentStatus> getStatus();
    
    /**
     * Response from the NLP Agent for general messages
     */
    class AgentResponse {
        private final String content;
        private final boolean success;
        private final String error;
        private final long processingTimeMs;
        private final int tokensUsed;
        
        public AgentResponse(String content, boolean success, String error, long processingTimeMs, int tokensUsed) {
            this.content = content;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.tokensUsed = tokensUsed;
        }
        
        public String getContent() { return content; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public int getTokensUsed() { return tokensUsed; }
    }
    
    /**
     * Response from query generation
     */
    class QueryGenerationResponse {
        private final String generatedQuery;
        private final String explanation;
        private final DatabaseType databaseType;
        private final boolean success;
        private final String error;
        private final long processingTimeMs;
        private final int tokensUsed;
        
        public QueryGenerationResponse(String generatedQuery, String explanation, DatabaseType databaseType, 
                                     boolean success, String error, long processingTimeMs, int tokensUsed) {
            this.generatedQuery = generatedQuery;
            this.explanation = explanation;
            this.databaseType = databaseType;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.tokensUsed = tokensUsed;
        }
        
        public String getGeneratedQuery() { return generatedQuery; }
        public String getExplanation() { return explanation; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public int getTokensUsed() { return tokensUsed; }
    }
    
    /**
     * Agent status information
     */
    class AgentStatus {
        private final boolean isHealthy;
        private final String version;
        private final long totalRequests;
        private final long successfulRequests;
        private final double averageResponseTime;
        
        public AgentStatus(boolean isHealthy, String version, long totalRequests, 
                          long successfulRequests, double averageResponseTime) {
            this.isHealthy = isHealthy;
            this.version = version;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.averageResponseTime = averageResponseTime;
        }
        
        public boolean isHealthy() { return isHealthy; }
        public String getVersion() { return version; }
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public double getAverageResponseTime() { return averageResponseTime; }
    }
}

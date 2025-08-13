package com.dbVybe.app.service.llm;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for LLM service implementations
 * This interface provides an extensible architecture for integrating different LLM providers
 * such as OpenAI, Anthropic, Groq, local models, etc.
 */
public interface LLMService {
    
    /**
     * Process a user message and generate a response using the configured LLM
     * 
     * @param userMessage The input message from the user
     * @param userId The ID of the user making the request
     * @param sessionId The session ID for context tracking
     * @return CompletableFuture containing the LLM response
     */
    CompletableFuture<LLMResponse> processMessage(String userMessage, String userId, String sessionId);
    
    /**
     * Get the name/identifier of this LLM service
     * 
     * @return String identifier for the LLM service (e.g., "groq", "openai", "claude")
     */
    String getServiceName();
    
    /**
     * Get the model name being used by this service
     * 
     * @return String model identifier (e.g., "openai/gpt-oss-120b", "gpt-4", "claude-3")
     */
    String getModelName();
    
    /**
     * Check if the LLM service is available and ready to process requests
     * 
     * @return CompletableFuture<Boolean> indicating service availability
     */
    CompletableFuture<Boolean> isHealthy();
    
    /**
     * Get current usage statistics for this LLM service
     * 
     * @return LLMUsageStats containing metrics about the service usage
     */
    LLMUsageStats getUsageStats();
    
    /**
     * Response wrapper for LLM service responses
     */
    class LLMResponse {
        private final String content;
        private final boolean success;
        private final String error;
        private final long processingTimeMs;
        private final int tokenCount;
        private final String modelUsed;
        
        public LLMResponse(String content, boolean success, String error, long processingTimeMs, int tokenCount, String modelUsed) {
            this.content = content;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.tokenCount = tokenCount;
            this.modelUsed = modelUsed;
        }
        
        // Success constructor
        public LLMResponse(String content, long processingTimeMs, int tokenCount, String modelUsed) {
            this(content, true, null, processingTimeMs, tokenCount, modelUsed);
        }
        
        // Error constructor
        public LLMResponse(String error, long processingTimeMs) {
            this(null, false, error, processingTimeMs, 0, null);
        }
        
        // Getters
        public String getContent() { return content; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public int getTokenCount() { return tokenCount; }
        public String getModelUsed() { return modelUsed; }
    }
    
    /**
     * Usage statistics for LLM service monitoring
     */
    class LLMUsageStats {
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final double averageResponseTimeMs;
        private final long totalTokensUsed;
        
        public LLMUsageStats(long totalRequests, long successfulRequests, long failedRequests, 
                           double averageResponseTimeMs, long totalTokensUsed) {
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.failedRequests = failedRequests;
            this.averageResponseTimeMs = averageResponseTimeMs;
            this.totalTokensUsed = totalTokensUsed;
        }
        
        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public double getAverageResponseTimeMs() { return averageResponseTimeMs; }
        public long getTotalTokensUsed() { return totalTokensUsed; }
        public double getSuccessRate() { 
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0; 
        }
    }
}

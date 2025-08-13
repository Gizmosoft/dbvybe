package com.dbVybe.app.service.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.List;
import java.time.Duration;

/**
 * Groq LLM Service implementation using openai/gpt-oss-120b model
 */
@Service
public class GroqLLMService implements LLMService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroqLLMService.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    @Value("${llm.groq.api-key:}")
    private String apiKey;
    
    @Value("${llm.groq.model:openai/gpt-oss-120b}")
    private String modelName;
    
    @Value("${llm.groq.max-tokens:1000}")
    private int maxTokens;
    
    @Value("${llm.groq.temperature:0.7}")
    private double temperature;
    
    @Value("${llm.groq.timeout:30000}")
    private int timeoutMs;
    
    private final WebClient webClient;
    
    // Usage statistics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    
    public GroqLLMService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
            .build();
    }
    
    @Override
    public CompletableFuture<LLMResponse> processMessage(String userMessage, String userId, String sessionId) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        logger.info("Processing message for user {} in session {} using Groq: {}", userId, sessionId, userMessage);
        
        // Check if API key is configured
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Groq API key not configured, using mock response");
            return CompletableFuture.completedFuture(createMockResponse(userMessage, startTime));
        }
        
        try {
            // Prepare request body for Groq API
            Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "messages", List.of(
                    Map.of("role", "system", "content", "You are a helpful database assistant that helps users with database queries and analysis. Provide clear, concise responses."),
                    Map.of("role", "user", "content", userMessage)
                ),
                "max_tokens", maxTokens,
                "temperature", temperature
            );
            
            return webClient.post()
                .uri(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .map(response -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    updateAverageResponseTime(processingTime);
                    
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> firstChoice = choices.get(0);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                            String content = (String) message.get("content");
                            
                            // Extract token usage
                            @SuppressWarnings("unchecked")
                            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                            int tokensUsed = usage != null ? (Integer) usage.getOrDefault("total_tokens", 0) : 0;
                            totalTokensUsed.addAndGet(tokensUsed);
                            
                            successfulRequests.incrementAndGet();
                            logger.info("Successfully processed message for user {} in {}ms using {} tokens", 
                                userId, processingTime, tokensUsed);
                            
                            return new LLMResponse(content, processingTime, tokensUsed, modelName);
                        } else {
                            failedRequests.incrementAndGet();
                            logger.error("No response choices from Groq API for user {}", userId);
                            return new LLMResponse("No response from LLM", processingTime);
                        }
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                        logger.error("Error parsing Groq response for user {}: {}", userId, e.getMessage());
                        return new LLMResponse("Error parsing LLM response: " + e.getMessage(), processingTime);
                    }
                })
                .onErrorResume(error -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    failedRequests.incrementAndGet();
                    logger.error("Error calling Groq API for user {}: {}", userId, error.getMessage());
                    return reactor.core.publisher.Mono.just(
                        new LLMResponse("LLM service error: " + error.getMessage(), processingTime)
                    );
                })
                .toFuture();
                
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            failedRequests.incrementAndGet();
            logger.error("Unexpected error processing message for user {}: {}", userId, e.getMessage());
            return CompletableFuture.completedFuture(
                new LLMResponse("Unexpected error: " + e.getMessage(), processingTime)
            );
        }
    }
    
    /**
     * Create a mock response when API key is not configured
     */
    private LLMResponse createMockResponse(String userMessage, long startTime) {
        try {
            Thread.sleep(100); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        successfulRequests.incrementAndGet();
        
        String mockResponse = String.format(
            "Mock Groq Response: I understand you asked '%s'. This is a simulated response from the Groq LLM service using model %s. " +
            "To enable real LLM processing, please configure your Groq API key in the application properties.", 
            userMessage, modelName
        );
        
        return new LLMResponse(mockResponse, processingTime, 50, modelName + " (mock)");
    }
    
    @Override
    public String getServiceName() {
        return "groq";
    }
    
    @Override
    public String getModelName() {
        return modelName;
    }
    
    @Override
    public CompletableFuture<Boolean> isHealthy() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return CompletableFuture.completedFuture(true); // Mock mode is always "healthy"
        }
        
        // Simple health check by making a minimal API call
        return processMessage("ping", "health-check", "health-session")
            .thenApply(LLMResponse::isSuccess)
            .exceptionally(throwable -> {
                logger.warn("Health check failed for Groq service: {}", throwable.getMessage());
                return false;
            });
    }
    
    @Override
    public LLMUsageStats getUsageStats() {
        return new LLMUsageStats(
            totalRequests.get(),
            successfulRequests.get(),
            failedRequests.get(),
            averageResponseTime.get(),
            totalTokensUsed.get()
        );
    }
    
    private void updateAverageResponseTime(long newResponseTime) {
        averageResponseTime.updateAndGet(currentAvg -> {
            long totalReqs = totalRequests.get();
            return totalReqs == 1 ? newResponseTime : (currentAvg * (totalReqs - 1) + newResponseTime) / totalReqs;
        });
    }
}

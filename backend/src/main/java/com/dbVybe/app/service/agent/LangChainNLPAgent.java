package com.dbVybe.app.service.agent;

import com.dbVybe.app.domain.model.DatabaseType;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.MemoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * LangChain4j-based Natural Language Processing Agent
 * Uses Groq LLM for intelligent query processing and generation
 */
@Service
public class LangChainNLPAgent implements NLPAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(LangChainNLPAgent.class);
    
    @Value("${llm.groq.api-key}")
    private String groqApiKey;
    
    @Value("${llm.groq.model:openai/gpt-oss-120b}")
    private String modelName;
    
    @Value("${llm.groq.max-tokens:1000}")
    private int maxTokens;
    
    @Value("${llm.groq.temperature:0.7}")
    private double temperature;
    
    @Value("${llm.groq.timeout:30000}")
    private int timeoutMs;
    
    // Statistics tracking
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    
    // LangChain4j AI Services
    private ChatLanguageModel chatModel;
    private DatabaseQueryAssistant queryAssistant;
    private GeneralChatAssistant generalAssistant;
    private QueryClassifier queryClassifier;
    
    // Chat memory provider for session-based conversations
    private ChatMemoryProvider chatMemoryProvider;
    
    // Patterns for query detection
    private static final Pattern QUERY_PATTERNS = Pattern.compile(
        "(?i).*(select|insert|update|delete|create|drop|alter|show|describe|explain|find|aggregate|count|sum|avg|group by|order by|where|from|join|database|table|column|record|data|query|search|filter|sort).*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    /**
     * Initialize the LangChain4j AI services
     */
    @PostConstruct
    public void initialize() {
        if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
            logger.warn("Groq API key not configured, NLP Agent will use mock responses");
            return;
        }
        
        try {
            // Configure the chat model for Groq
            this.chatModel = OpenAiChatModel.builder()
                .apiKey(groqApiKey)
                .baseUrl("https://api.groq.com/openai/v1") // Groq OpenAI-compatible endpoint
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .timeout(Duration.ofMillis(timeoutMs))
                .build();
            
            // Configure chat memory provider for session-based conversations
            this.chatMemoryProvider = memoryId -> MessageWindowChatMemory.withMaxMessages(10);
            
            // Create specialized AI assistants
            this.queryAssistant = AiServices.builder(DatabaseQueryAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
            
            this.generalAssistant = AiServices.builder(GeneralChatAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
            
            this.queryClassifier = AiServices.builder(QueryClassifier.class)
                .chatLanguageModel(chatModel)
                .build();
            
            logger.info("LangChain4j NLP Agent initialized successfully with Groq model: {}", modelName);
            
        } catch (Exception e) {
            logger.error("Failed to initialize LangChain4j NLP Agent: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public CompletableFuture<AgentResponse> processGeneralMessage(String message, String userId, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            totalRequests.incrementAndGet();
            
            try {
                logger.info("Processing general message for user {} in session {}: {}", userId, sessionId, message);
                
                if (generalAssistant == null) {
                    return createMockGeneralResponse(message, startTime);
                }
                
                String response = generalAssistant.chat(message, userId, sessionId);
                
                long processingTime = System.currentTimeMillis() - startTime;
                updateAverageResponseTime(processingTime);
                successfulRequests.incrementAndGet();
                
                logger.info("Successfully processed general message for user {} in {}ms", userId, processingTime);
                return new AgentResponse(response, true, null, processingTime, estimateTokens(message + response));
                
            } catch (Exception e) {
                long processingTime = System.currentTimeMillis() - startTime;
                logger.error("Error processing general message for user {}: {}", userId, e.getMessage(), e);
                return new AgentResponse("I'm sorry, I encountered an error processing your message.", false, e.getMessage(), processingTime, 0);
            }
        });
    }
    
    @Override
    public CompletableFuture<QueryGenerationResponse> generateDatabaseQuery(
            String message, String userId, String sessionId, 
            DatabaseType databaseType, Map<String, Object> databaseSchema) {
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            totalRequests.incrementAndGet();
            
            try {
                logger.info("Generating database query for user {} in session {} for {} database: {}", 
                    userId, sessionId, databaseType, message);
                
                if (queryAssistant == null) {
                    return createMockQueryResponse(message, databaseType, startTime);
                }
                
                String schemaContext = buildSchemaContext(databaseSchema, databaseType);
                String queryResult = queryAssistant.generateQuery(message, databaseType.toString(), schemaContext, userId);
                
                // Parse the result to extract query and explanation
                String[] parts = queryResult.split("\\n\\n", 2);
                String query = parts[0].trim();
                String explanation = parts.length > 1 ? parts[1].trim() : "Database query generated successfully.";
                
                long processingTime = System.currentTimeMillis() - startTime;
                updateAverageResponseTime(processingTime);
                successfulRequests.incrementAndGet();
                
                logger.info("Successfully generated query for user {} in {}ms", userId, processingTime);
                return new QueryGenerationResponse(query, explanation, databaseType, true, null, processingTime, 
                    estimateTokens(message + queryResult));
                
            } catch (Exception e) {
                long processingTime = System.currentTimeMillis() - startTime;
                logger.error("Error generating query for user {}: {}", userId, e.getMessage(), e);
                return new QueryGenerationResponse(null, null, databaseType, false, e.getMessage(), processingTime, 0);
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> requiresQueryGeneration(String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Classifying message for query generation requirement: {}", message);
                
                // First, use pattern matching for quick detection
                if (QUERY_PATTERNS.matcher(message).matches()) {
                    logger.debug("Pattern match indicates query generation required");
                    return true;
                }
                
                // Use AI classifier for more sophisticated detection
                if (queryClassifier != null) {
                    boolean requiresQuery = queryClassifier.requiresQuery(message);
                    logger.debug("AI classifier result for query requirement: {}", requiresQuery);
                    return requiresQuery;
                }
                
                // Fallback to pattern matching only
                return QUERY_PATTERNS.matcher(message).matches();
                
            } catch (Exception e) {
                logger.error("Error classifying message: {}", e.getMessage(), e);
                // Default to false to avoid unnecessary processing
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<AgentStatus> getStatus() {
        return CompletableFuture.completedFuture(new AgentStatus(
            chatModel != null,
            "1.0.0-langchain4j",
            totalRequests.get(),
            successfulRequests.get(),
            averageResponseTime.get()
        ));
    }
    
    /**
     * Build schema context string for the AI model
     */
    private String buildSchemaContext(Map<String, Object> databaseSchema, DatabaseType databaseType) {
        if (databaseSchema == null || databaseSchema.isEmpty()) {
            return "No schema information available.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Database Type: ").append(databaseType).append("\n");
        context.append("Schema Information:\n");
        
        for (Map.Entry<String, Object> entry : databaseSchema.entrySet()) {
            context.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        return context.toString();
    }
    
    /**
     * Create mock response for general messages when API is not available
     */
    private AgentResponse createMockGeneralResponse(String message, long startTime) {
        try {
            Thread.sleep(100); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        successfulRequests.incrementAndGet();
        
        String mockResponse = String.format(
            "Mock NLP Agent Response: I understand your message '%s'. This is a simulated response from the LangChain4j NLP Agent. " +
            "To enable real AI processing, please configure your Groq API key.", 
            message
        );
        
        return new AgentResponse(mockResponse, true, null, processingTime, 50);
    }
    
    /**
     * Create mock query response when API is not available
     */
    private QueryGenerationResponse createMockQueryResponse(String message, DatabaseType databaseType, long startTime) {
        try {
            Thread.sleep(150); // Simulate processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        successfulRequests.incrementAndGet();
        
        String mockQuery = generateMockQuery(message, databaseType);
        String mockExplanation = String.format(
            "Mock Query Generation: This is a simulated %s query based on your request '%s'. " +
            "To enable real AI-powered query generation, please configure your Groq API key.", 
            databaseType, message
        );
        
        return new QueryGenerationResponse(mockQuery, mockExplanation, databaseType, true, null, processingTime, 75);
    }
    
    /**
     * Generate a simple mock query based on database type
     */
    private String generateMockQuery(String message, DatabaseType databaseType) {
        switch (databaseType) {
            case MYSQL:
            case POSTGRESQL:
                return "SELECT * FROM users WHERE name LIKE '%example%' LIMIT 10;";
            case MONGODB:
                return "{ \"find\": \"users\", \"filter\": { \"name\": { \"$regex\": \"example\", \"$options\": \"i\" } }, \"limit\": 10 }";
            default:
                return "-- Mock query for " + databaseType;
        }
    }
    
    /**
     * Estimate token count (rough approximation)
     */
    private int estimateTokens(String text) {
        return text.split("\\s+").length + (text.length() / 4);
    }
    
    /**
     * Update average response time
     */
    private void updateAverageResponseTime(long newResponseTime) {
        averageResponseTime.updateAndGet(currentAvg -> {
            long totalReqs = totalRequests.get();
            return totalReqs == 1 ? newResponseTime : (currentAvg * (totalReqs - 1) + newResponseTime) / totalReqs;
        });
    }
    
    /**
     * AI Service interface for database query generation
     */
    interface DatabaseQueryAssistant {
        @SystemMessage("You are a database query expert. Generate safe, optimized, read-only database queries based on user requests. " +
                      "Always include an explanation of what the query does. Format your response as: QUERY on first lines, then blank line, then EXPLANATION.")
        String generateQuery(@UserMessage String userRequest, @V("databaseType") String databaseType, 
                            @V("schemaContext") String schemaContext, @MemoryId String userId);
    }
    
    /**
     * AI Service interface for general chat
     */
    interface GeneralChatAssistant {
        @SystemMessage("You are a helpful database assistant. Provide clear, concise answers to user questions. " +
                      "Be friendly and professional. If the question is not database-related, still try to be helpful.")
        String chat(@UserMessage String message, @MemoryId String userId, @V("sessionId") String sessionId);
    }
    
    /**
     * AI Service interface for classifying whether a message requires query generation
     */
    interface QueryClassifier {
        @SystemMessage("You are a query classifier. Determine if a user message requires database query generation. " +
                      "Return only 'true' if the user is asking for data retrieval, analysis, or any database operation. " +
                      "Return 'false' for general questions, greetings, or non-database requests.")
        @UserMessage("Does this message require database query generation? Message: {{it}}")
        boolean requiresQuery(String message);
    }
}

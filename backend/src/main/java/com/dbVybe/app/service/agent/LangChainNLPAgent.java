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
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

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
                
                // Extract graph context from the combined schema
                Map<String, Object> graphContext = new HashMap<>();
                if (databaseSchema.containsKey("graphContext")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extractedGraphContext = (Map<String, Object>) databaseSchema.get("graphContext");
                    graphContext = extractedGraphContext;
                    logger.info("Extracted graph context with {} relationships", 
                        graphContext.containsKey("relationships") ? ((List<?>) graphContext.get("relationships")).size() : 0);
                }
                
                String schemaContext = buildSchemaContext(databaseSchema, graphContext);
                logger.info("Schema context being passed to LLM ({} chars):\n{}", schemaContext.length(), schemaContext);
                
                String queryResult = queryAssistant.generateQuery(message, databaseType.toString(), schemaContext, userId);
                
                logger.debug("Raw query result from LLM: {}", queryResult);
                
                // Parse the result to extract query and explanation
                String[] parts = parseQueryResult(queryResult);
                String query = parts[0];
                String explanation = parts[1];
                
                // Check if the query is valid SQL, if not, generate a fallback query
                if (!isValidSQLQuery(query)) {
                    logger.warn("LLM generated invalid SQL, generating fallback query for: {}", message);
                    query = generateFallbackQuery(message, databaseSchema, databaseType);
                    explanation = "Generated fallback query using available schema information.";
                }
                
                // ENFORCE SCHEMA PREFIXES: Add schema prefixes to queries that don't have them
                String enforcedQuery = enforceSchemaPrefixes(query, databaseSchema, databaseType);
                if (!enforcedQuery.equals(query)) {
                    logger.info("Enforced schema prefixes. Original: '{}' -> Enforced: '{}'", query, enforcedQuery);
                    query = enforcedQuery;
                }
                
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
     * Enforce schema prefixes on table names in queries
     */
    private String enforceSchemaPrefixes(String query, Map<String, Object> databaseSchema, DatabaseType databaseType) {
        if (query == null || query.trim().isEmpty() || databaseSchema == null || !databaseSchema.containsKey("tables")) {
            return query;
        }
        
        logger.debug("Enforcing schema prefixes on query: {}", query);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) databaseSchema.get("tables");
        
        // Create a map of table names without schema to their full schema-qualified names
        Map<String, String> tableNameMap = new HashMap<>();
        for (Map<String, Object> table : tables) {
            String fullTableName = (String) table.get("tableName");
            if (fullTableName != null && fullTableName.contains(".")) {
                String[] parts = fullTableName.split("\\.");
                String tableNameOnly = parts[parts.length - 1]; // Get just the table name part
                tableNameMap.put(tableNameOnly.toLowerCase(), fullTableName);
                logger.debug("Mapping table name '{}' to '{}'", tableNameOnly, fullTableName);
            }
        }
        
        if (tableNameMap.isEmpty()) {
            logger.debug("No schema-qualified table names found, returning original query");
            return query;
        }
        
        // Replace table names in the query
        String modifiedQuery = query;
        for (Map.Entry<String, String> entry : tableNameMap.entrySet()) {
            String tableNameOnly = entry.getKey();
            String fullTableName = entry.getValue();
            
            // Pattern to match table names in SQL (after FROM, JOIN, etc.)
            String pattern = "(?i)\\b(FROM|JOIN|UPDATE|INTO)\\s+([\"`]?)(" + tableNameOnly + ")([\"`]?)\\b";
            String replacement = "$1 $2" + fullTableName + "$4";
            
            String beforeReplacement = modifiedQuery;
            modifiedQuery = modifiedQuery.replaceAll(pattern, replacement);
            
            if (!beforeReplacement.equals(modifiedQuery)) {
                logger.debug("Replaced table name '{}' with '{}'", tableNameOnly, fullTableName);
            }
        }
        
        logger.debug("Schema prefix enforcement complete. Original: '{}' -> Modified: '{}'", query, modifiedQuery);
        return modifiedQuery;
    }
    
    /**
     * Build comprehensive schema context including graph relationships
     */
    private String buildSchemaContext(Map<String, Object> databaseSchema, Map<String, Object> graphContext) {
        if (databaseSchema == null || databaseSchema.isEmpty()) {
            return "No schema information available.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("DATABASE SCHEMA INFORMATION:\n\n");
        
        // Add tables and columns
        if (databaseSchema.containsKey("tables")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) databaseSchema.get("tables");
            
            context.append("TABLES AND COLUMNS:\n");
            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("name");
                String tableComment = (String) table.get("comment");
                
                context.append("TABLE: ").append(tableName);
                if (tableComment != null && !tableComment.trim().isEmpty()) {
                    context.append(" (").append(tableComment).append(")");
                }
                context.append("\n");
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");
                if (columns != null) {
                    for (Map<String, Object> column : columns) {
                        String columnName = (String) column.get("name");
                        String dataType = (String) column.get("dataType");
                        Boolean nullable = (Boolean) column.get("nullable");
                        String comment = (String) column.get("comment");
                        
                        context.append("  COLUMN: ").append(columnName).append(" (").append(dataType).append(")");
                        if (nullable != null && !nullable) {
                            context.append(" NOT NULL");
                        }
                        if (comment != null && !comment.trim().isEmpty()) {
                            context.append(" - ").append(comment);
                        }
                        context.append("\n");
                    }
                }
                context.append("\n");
            }
        }
        
        // Add graph context information
        if (graphContext != null && !graphContext.isEmpty()) {
            context.append("TABLE RELATIONSHIPS AND JOIN PATHS:\n");
            
            // Add foreign key relationships
            if (graphContext.containsKey("relationships")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relationships = (List<Map<String, Object>>) graphContext.get("relationships");
                
                if (!relationships.isEmpty()) {
                    context.append("FOREIGN KEY RELATIONSHIPS:\n");
                    for (Map<String, Object> rel : relationships) {
                        String sourceTable = (String) rel.get("sourceTable");
                        String sourceColumn = (String) rel.get("sourceColumn");
                        String targetTable = (String) rel.get("targetTable");
                        String targetColumn = (String) rel.get("targetColumn");
                        String relationshipType = (String) rel.get("relationshipType");
                        
                        context.append("  ").append(sourceTable).append(".").append(sourceColumn)
                               .append(" -> ").append(targetTable).append(".").append(targetColumn)
                               .append(" (").append(relationshipType).append(")\n");
                    }
                    context.append("\n");
                }
            }
            
            // Add join paths
            if (graphContext.containsKey("joinPaths")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> joinPaths = (List<Map<String, Object>>) graphContext.get("joinPaths");
                
                if (!joinPaths.isEmpty()) {
                    context.append("COMMON JOIN PATHS:\n");
                    for (Map<String, Object> path : joinPaths) {
                        @SuppressWarnings("unchecked")
                        List<String> pathTables = (List<String>) path.get("path");
                        String description = (String) path.get("description");
                        String complexity = (String) path.get("complexity");
                        String joinCondition = (String) path.get("joinCondition");
                        
                        context.append("  PATH: ").append(String.join(" -> ", pathTables))
                               .append(" (").append(complexity).append(")\n");
                        context.append("    Description: ").append(description).append("\n");
                        if (joinCondition != null) {
                            context.append("    Join Condition: ").append(joinCondition).append("\n");
                        }
                        context.append("\n");
                    }
                }
            }
            
            // Add metadata
            if (graphContext.containsKey("metadata")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) graphContext.get("metadata");
                context.append("RELATIONSHIP METADATA:\n");
                context.append("  Total Relationships: ").append(metadata.get("totalRelationships")).append("\n");
                context.append("  Total Join Paths: ").append(metadata.get("totalJoinPaths")).append("\n");
                context.append("\n");
            }
        }
        
        context.append("INSTRUCTIONS:\n");
        context.append("- Use exact table names with schema prefixes (e.g., 'pizza_shop.customer')\n");
        context.append("- Use exact column names from the schema (e.g., 'customer_id', not 'id')\n");
        context.append("- For joins, use the relationship information provided above\n");
        context.append("- Generate direct SQL queries with actual values, not parameterized queries\n");
        context.append("- Follow the join paths for complex multi-table queries\n");
        
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
     * Parse query result from LLM response
     */
    private String[] parseQueryResult(String queryResult) {
        if (queryResult == null || queryResult.trim().isEmpty()) {
            return new String[]{"", "No query generated"};
        }
        
        logger.debug("Parsing query result: {}", queryResult);
        
        // Try to extract query from markdown code blocks first
        String query = extractQueryFromMarkdown(queryResult);
        if (query != null) {
            String explanation = extractExplanation(queryResult, query);
            return new String[]{query, explanation};
        }
        
        // Fallback to simple split on double newline
        String[] parts = queryResult.split("\\n\\n", 2);
        String extractedQuery = parts[0].trim();
        String extractedExplanation = parts.length > 1 ? parts[1].trim() : "Database query generated successfully.";
        
        // Clean up the query
        extractedQuery = cleanQueryFromResponse(extractedQuery);
        
        return new String[]{extractedQuery, extractedExplanation};
    }
    
    /**
     * Extract query from markdown code blocks
     */
    private String extractQueryFromMarkdown(String response) {
        // Look for ```sql ... ``` blocks
        String sqlPattern = "```sql\\s*([\\s\\S]*?)\\s*```";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(sqlPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Look for ``` ... ``` blocks (any language)
        String codePattern = "```\\s*([\\s\\S]*?)\\s*```";
        pattern = java.util.regex.Pattern.compile(codePattern);
        matcher = pattern.matcher(response);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * Extract explanation from response
     */
    private String extractExplanation(String response, String query) {
        // Remove the query part from the response
        String withoutQuery = response.replace(query, "").trim();
        
        // Remove markdown formatting
        withoutQuery = withoutQuery.replaceAll("```[^`]*```", "").trim();
        withoutQuery = withoutQuery.replaceAll("\\*\\*[^*]+\\*\\*", "").trim();
        
        // Clean up extra whitespace
        withoutQuery = withoutQuery.replaceAll("\\s+", " ").trim();
        
        if (withoutQuery.isEmpty()) {
            return "Database query generated successfully.";
        }
        
        return withoutQuery;
    }
    
    /**
     * Clean query from response formatting
     */
    private String cleanQueryFromResponse(String query) {
        if (query == null) return "";
        
        // Remove markdown formatting
        query = query.replaceAll("\\*\\*[^*]+\\*\\*", "").trim();
        
        // Remove the word "QUERY" at the beginning (case insensitive)
        query = query.replaceAll("(?i)^\\s*QUERY\\s+", "");
        
        // Remove the word "EXPLANATION" at the beginning (case insensitive)
        query = query.replaceAll("(?i)^\\s*EXPLANATION\\s+", "");
        
        // Remove extra whitespace
        query = query.replaceAll("\\s+", " ").trim();
        
        return query;
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
     * Check if the query is actually valid SQL (not explanatory text)
     */
    private boolean isValidSQLQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String upperQuery = query.toUpperCase().trim();
        
        // Check if it starts with common SQL keywords
        if (upperQuery.startsWith("SELECT ") || 
            upperQuery.startsWith("INSERT ") || 
            upperQuery.startsWith("UPDATE ") || 
            upperQuery.startsWith("DELETE ") || 
            upperQuery.startsWith("CREATE ") || 
            upperQuery.startsWith("DROP ") || 
            upperQuery.startsWith("ALTER ") || 
            upperQuery.startsWith("SHOW ") || 
            upperQuery.startsWith("DESCRIBE ") || 
            upperQuery.startsWith("EXPLAIN ") ||
            upperQuery.startsWith("WITH ")) {
            return true;
        }
        
        // Check if it contains explanatory text patterns
        if (upperQuery.startsWith("I'M ") || 
            upperQuery.startsWith("I CAN ") || 
            upperQuery.startsWith("I NEED ") || 
            upperQuery.startsWith("COULD YOU ") || 
            upperQuery.startsWith("PLEASE ") ||
            upperQuery.contains("BUT I NEED") ||
            upperQuery.contains("COULD YOU PROVIDE") ||
            upperQuery.contains("SCHEMA-QUALIFIED")) {
            return false;
        }
        
        // Check if it looks like a natural language response
        if (upperQuery.length() > 100 && 
            (upperQuery.contains(" ") && upperQuery.split(" ").length > 10)) {
            // If it's a long text without SQL keywords, it's probably explanatory text
            return false;
        }
        
        return true;
    }
    
    /**
     * Generate a fallback query when the LLM fails to provide valid SQL
     */
    private String generateFallbackQuery(String message, Map<String, Object> databaseSchema, DatabaseType databaseType) {
        String lowerMessage = message.toLowerCase();
        
        // Extract table names and column information from schema
        List<String> tableNames = new ArrayList<>();
        Map<String, List<String>> tableColumns = new HashMap<>();
        
        if (databaseSchema != null && databaseSchema.containsKey("tables")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) databaseSchema.get("tables");
            for (Map<String, Object> table : tables) {
                String tableName = (String) table.get("tableName");
                if (tableName != null) {
                    tableNames.add(tableName);
                    
                    // Extract column names for this table
                    List<String> columns = new ArrayList<>();
                    if (table.containsKey("columns")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> columnList = (List<Map<String, Object>>) table.get("columns");
                        for (Map<String, Object> column : columnList) {
                            String columnName = (String) column.get("name");
                            if (columnName != null) {
                                columns.add(columnName);
                            }
                        }
                    }
                    tableColumns.put(tableName, columns);
                }
            }
        }
        
        // Generate basic queries based on message content
        if (lowerMessage.contains("payment")) {
            for (String tableName : tableNames) {
                if (tableName.toLowerCase().contains("payment")) {
                    return "SELECT * FROM " + tableName + " LIMIT 10";
                }
            }
        } else if (lowerMessage.contains("customer")) {
            for (String tableName : tableNames) {
                if (tableName.toLowerCase().contains("customer")) {
                    return "SELECT * FROM " + tableName + " LIMIT 10";
                }
            }
        } else if (lowerMessage.contains("order")) {
            for (String tableName : tableNames) {
                if (tableName.toLowerCase().contains("order")) {
                    return "SELECT * FROM " + tableName + " LIMIT 10";
                }
            }
        } else if (lowerMessage.contains("table")) {
            // Show all tables
            switch (databaseType) {
                case POSTGRESQL:
                    return "SELECT schemaname, tablename FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema') ORDER BY schemaname, tablename";
                case MYSQL:
                    return "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('information_schema', 'mysql', 'performance_schema') ORDER BY table_schema, table_name";
                default:
                    return "SELECT * FROM " + (tableNames.isEmpty() ? "unknown_table" : tableNames.get(0)) + " LIMIT 10";
            }
        }
        
        // Try to generate joins for complex queries
        if (lowerMessage.contains("join") || lowerMessage.contains("with") || lowerMessage.contains("and")) {
            // Look for order-customer join
            String orderTable = null;
            String customerTable = null;
            String paymentTable = null;
            
            for (String tableName : tableNames) {
                if (tableName.toLowerCase().contains("order")) {
                    orderTable = tableName;
                } else if (tableName.toLowerCase().contains("customer")) {
                    customerTable = tableName;
                } else if (tableName.toLowerCase().contains("payment")) {
                    paymentTable = tableName;
                }
            }
            
            // Generate order-customer join
            if (orderTable != null && customerTable != null) {
                List<String> orderColumns = tableColumns.get(orderTable);
                List<String> customerColumns = tableColumns.get(customerTable);
                
                if (orderColumns != null && customerColumns != null) {
                    // Find the foreign key column in order table
                    String customerIdColumn = null;
                    for (String col : orderColumns) {
                        if (col.toLowerCase().contains("customer")) {
                            customerIdColumn = col;
                            break;
                        }
                    }
                    
                    if (customerIdColumn != null) {
                        // Find the primary key column in customer table
                        String customerPkColumn = null;
                        for (String col : customerColumns) {
                            if (col.toLowerCase().contains("customer") && col.toLowerCase().contains("id")) {
                                customerPkColumn = col;
                                break;
                            }
                        }
                        
                        if (customerPkColumn != null) {
                            return "SELECT o.*, c.* FROM " + orderTable + " AS o JOIN " + customerTable + " AS c ON o." + customerIdColumn + " = c." + customerPkColumn + " LIMIT 10";
                        }
                    }
                }
            }
            
            // Generate payment-order-customer join
            if (paymentTable != null && orderTable != null && customerTable != null) {
                List<String> paymentColumns = tableColumns.get(paymentTable);
                List<String> orderColumns = tableColumns.get(orderTable);
                List<String> customerColumns = tableColumns.get(customerTable);
                
                if (paymentColumns != null && orderColumns != null && customerColumns != null) {
                    // Find foreign key columns
                    String paymentOrderIdColumn = null;
                    String orderCustomerIdColumn = null;
                    String customerPkColumn = null;
                    
                    for (String col : paymentColumns) {
                        if (col.toLowerCase().contains("order")) {
                            paymentOrderIdColumn = col;
                            break;
                        }
                    }
                    
                    for (String col : orderColumns) {
                        if (col.toLowerCase().contains("customer")) {
                            orderCustomerIdColumn = col;
                            break;
                        }
                    }
                    
                    for (String col : customerColumns) {
                        if (col.toLowerCase().contains("customer") && col.toLowerCase().contains("id")) {
                            customerPkColumn = col;
                            break;
                        }
                    }
                    
                    if (paymentOrderIdColumn != null && orderCustomerIdColumn != null && customerPkColumn != null) {
                        return "SELECT p.*, c.* FROM " + paymentTable + " AS p JOIN " + orderTable + " AS o ON p." + paymentOrderIdColumn + " = o.order_id JOIN " + customerTable + " AS c ON o." + orderCustomerIdColumn + " = c." + customerPkColumn + " LIMIT 10";
                    }
                }
            }
            
            // Handle payment-specific queries with amount filtering
            if (lowerMessage.contains("payment") && (lowerMessage.contains("over") || lowerMessage.contains("more than") || lowerMessage.contains(">") || lowerMessage.contains("amount"))) {
                if (paymentTable != null && orderTable != null && customerTable != null) {
                    List<String> paymentColumns = tableColumns.get(paymentTable);
                    List<String> orderColumns = tableColumns.get(orderTable);
                    List<String> customerColumns = tableColumns.get(customerTable);
                    
                    if (paymentColumns != null && orderColumns != null && customerColumns != null) {
                        // Extract amount from message (simplified)
                        String amountFilter = "50"; // Default
                        if (lowerMessage.contains("$20")) amountFilter = "20";
                        else if (lowerMessage.contains("$50")) amountFilter = "50";
                        else if (lowerMessage.contains("$100")) amountFilter = "100";
                        
                        // Find the correct columns
                        String paymentOrderIdColumn = null;
                        String orderCustomerIdColumn = null;
                        String customerPkColumn = null;
                        
                        for (String col : paymentColumns) {
                            if (col.toLowerCase().contains("order")) {
                                paymentOrderIdColumn = col;
                                break;
                            }
                        }
                        
                        for (String col : orderColumns) {
                            if (col.toLowerCase().contains("customer")) {
                                orderCustomerIdColumn = col;
                                break;
                            }
                        }
                        
                        for (String col : customerColumns) {
                            if (col.toLowerCase().contains("customer") && col.toLowerCase().contains("id")) {
                                customerPkColumn = col;
                                break;
                            }
                        }
                        
                        if (paymentOrderIdColumn != null && orderCustomerIdColumn != null && customerPkColumn != null) {
                            return "SELECT DISTINCT c.* FROM " + customerTable + " AS c JOIN " + orderTable + " AS o ON c." + customerPkColumn + " = o." + orderCustomerIdColumn + " JOIN " + paymentTable + " AS p ON o.order_id = p." + paymentOrderIdColumn + " WHERE p.amount > " + amountFilter;
                        }
                    }
                }
            }
        }
        
        // Default fallback
        if (!tableNames.isEmpty()) {
            return "SELECT * FROM " + tableNames.get(0) + " LIMIT 10";
        } else {
            return "SELECT 1 as test";
        }
    }
    
    /**
     * AI Service interface for database query generation
     */
    interface DatabaseQueryAssistant {
        @SystemMessage("You are a database query expert named DbVybe. Generate ONLY valid SQL queries based on user requests. " +
                      "CRITICAL: You MUST generate actual SQL queries, NOT explanatory text or requests for more information. " +
                      "CRITICAL REQUIREMENT: You MUST use ONLY the exact table names and column names provided in the schema context. " +
                      "The schema context contains the REAL tables and columns from the connected database. " +
                      "For PostgreSQL, table names include schema prefixes (e.g., 'pizza_shop.customer', 'pizza_shop.order'). " +
                      "NEVER use table names without schema prefixes - always use the full schema-qualified name. " +
                      "Look for the 'TABLE:' sections in the schema context and use those exact names. " +
                      "Do not assume, guess, or modify table names - only use what is explicitly provided. " +
                      "If you see 'pizza_shop.customer' in the schema, use 'pizza_shop.customer' in your query, NOT just 'customer'. " +
                      "IMPORTANT: Generate DIRECT SQL queries with actual values, NOT parameterized queries. " +
                      "Use actual values like '20', '$50', 'John' instead of placeholders like $1, $2, ?. " +
                      "For example: 'SELECT * FROM pizza_shop.payment WHERE amount > 20' NOT 'SELECT * FROM pizza_shop.payment WHERE amount > $1'. " +
                      "NEVER ask for more information or explain what you need - just generate the SQL query. " +
                      "If you don't have enough information in the schema context, generate a query using information_schema to get the needed details. " +
                      "GRAPH CONTEXT: Use the provided graph context to understand table relationships and generate proper JOINs. " +
                      "The graph context contains foreign key relationships and join paths that show how tables are connected. " +
                      "When joining tables, use the exact column names from the relationships section. " +
                      "For example, if the graph shows 'pizza_shop.order.customer_id -> pizza_shop.customer.customer_id', " +
                      "use 'o.customer_id = c.customer_id' in your JOIN condition. " +
                      "RELATIONSHIP GUIDANCE: " +
                      "- If the user asks for data from multiple tables, use the graph context to find the correct join paths. " +
                      "- For complex joins (3+ tables), follow the join paths provided in the graph context. " +
                      "- Always use the exact column names from the relationships, not assumed names like 'id'. " +
                      "- If the graph shows 'sourceColumn -> targetColumn', use that exact mapping in your JOIN. " +
                      "Format your response as: QUERY on first lines, then blank line, then EXPLANATION. " +
                      "Do not include markdown formatting like **QUERY** or ```sql``` blocks in your response. " +
                      "Do not include the word 'QUERY' before the actual SQL statement - just write the SQL directly. " +
                      "For PostgreSQL table discovery, prefer using pg_tables over information_schema.tables for better compatibility. " +
                      "When asked about tables, check all schemas, not just 'public'. " +
                      "Keep explanations brief and to the point. " +
                      "If the user asks for data (like 'list all tables or which table holds X data'), focus on providing only the required data by fetching it from the database in concern and form a simple sentence around it to make it more human readable. " +
                      "If the user asks for a database query specifically, provide the exact query for their database along with a brief explanation of what the query does. " +
                      "ALWAYS verify that the table names and column names in your query exist in the provided schema before generating the query. " +
                      "REMEMBER: Use schema-qualified table names like 'pizza_shop.customer', not just 'customer'. " +
                      "REMEMBER: Use actual values in queries, not parameter placeholders. " +
                      "REMEMBER: Use graph context relationships for proper JOINs. " +
                      "REMEMBER: Generate ONLY SQL queries, never explanatory text asking for more information.")
        String generateQuery(@UserMessage String userRequest, @V("databaseType") String databaseType, 
                            @V("schemaContext") String schemaContext, @MemoryId String userId);
    }
    
    /**
     * AI Service interface for general chat
     */
    interface GeneralChatAssistant {
        @SystemMessage("You are a helpful database assistant. Provide clear, concise answers to user questions. " +
                      "Be friendly and professional. If the question is not database-related, still try to be helpful. " +
                      "Keep responses brief and direct. Avoid unnecessary explanations or boilerplate text.")
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

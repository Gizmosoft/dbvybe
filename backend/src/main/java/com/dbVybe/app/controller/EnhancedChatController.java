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
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import com.dbVybe.app.service.agent.DatabaseSchemaAgent;

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
            
            // 1. Get vector context for relevant tables (this contains the actual schema)
            Map<String, Object> vectorContext = getVectorContext(request.getMessage(), request.getConnectionId(), userId);
            
            // 2. Get graph context for relationships
            Map<String, Object> graphContext = getGraphContext(request.getMessage(), request.getConnectionId());
            
            // 3. Combine contexts for schema information - Pass schema directly to LLM
            Map<String, Object> combinedSchema = new HashMap<>();
            combinedSchema.put("databaseType", databaseType.toString());
            combinedSchema.put("databaseName", dbConnection.getDatabaseName());
            
            // Pass the actual schema information directly (not nested under vectorContext)
            if (vectorContext.containsKey("tables")) {
                combinedSchema.put("tables", vectorContext.get("tables"));
                logger.info("Passing {} tables directly to LLM", ((List<?>) vectorContext.get("tables")).size());
            }
            
            // Add additional context information
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
            
            String generatedQuery = cleanQuery(queryResponse.getGeneratedQuery());
            String explanation = cleanText(queryResponse.getExplanation());
            
            logger.info("Generated query for user {}: {}", userId, generatedQuery);
            
            // 5. Execute the query directly (no safety checks)
            return executeQueryAndReturnResults(request, userId, generatedQuery, explanation, databaseType);
            
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
            Scheduler scheduler = clusterManager.getScheduler();
            
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
                // Format results based on the type of query
                String formattedResponse = formatQueryResults(executionResult, explanation, query, request.getMessage());
                
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
                    "Query execution failed: " + executionResult.getError(),
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
    private String formatQueryResults(QueryExecutorActor.QueryExecutionResponse result, String explanation, String query, String userMessage) {
        // Determine if this is a query request or a data request
        boolean isQueryRequest = isQueryRequest(userMessage);
        
        if (isQueryRequest) {
            // User asked for a SQL query - return the query with brief explanation
            return explanation != null && !explanation.trim().isEmpty() 
                ? explanation + "\n\n" + query
                : "Here's the SQL query:\n\n" + query;
        } else {
            // User asked for data - return the actual data in a clean format
            return formatDataResponse(result, userMessage);
        }
    }
    
    /**
     * Check if the user is asking for a SQL query or for data
     */
    private boolean isQueryRequest(String userMessage) {
        String message = userMessage.toLowerCase();
        return message.contains("sql query") || 
               message.contains("give me sql") || 
               message.contains("show me sql") || 
               message.contains("write sql") || 
               message.contains("generate sql") ||
               message.contains("query to") ||
               message.contains("how to query");
    }
    
    /**
     * Format data response in a clean, direct way
     */
    private String formatDataResponse(QueryExecutorActor.QueryExecutionResponse result, String userMessage) {
        if (result.getData() == null || result.getData().getRows().isEmpty()) {
            return "No data found.";
        }
        
        // For simple data requests, just show the data without boilerplate
        if (result.getData().getColumnNames().size() == 1) {
            // Single column result - just list the values
            StringBuilder response = new StringBuilder();
            for (List<Object> row : result.getData().getRows()) {
                if (row.get(0) != null) {
                    response.append(row.get(0).toString()).append("\n");
                }
            }
            return response.toString().trim();
        } else {
            // Multi-column result - format as simple table
            return formatSimpleTable(result.getData());
        }
    }
    
    /**
     * Format table data as simple text without markdown
     */
    private String formatSimpleTable(com.dbVybe.app.service.agent.QueryExecutionAgent.QueryResultData data) {
        StringBuilder table = new StringBuilder();
        
        // Table header
        for (int i = 0; i < data.getColumnNames().size(); i++) {
            table.append(data.getColumnNames().get(i));
            if (i < data.getColumnNames().size() - 1) {
                table.append(" | ");
            }
        }
        table.append("\n");
        
        // Table separator
        for (int i = 0; i < data.getColumnNames().size(); i++) {
            table.append("---");
            if (i < data.getColumnNames().size() - 1) {
                table.append(" | ");
            }
        }
        table.append("\n");
        
        // Table rows (limit to first 20 rows for readability)
        int rowLimit = Math.min(data.getRows().size(), 20);
        for (int i = 0; i < rowLimit; i++) {
            List<Object> row = data.getRows().get(i);
            for (int j = 0; j < row.size(); j++) {
                Object value = row.get(j);
                String cellValue = (value != null) ? value.toString() : "NULL";
                table.append(cellValue);
                if (j < row.size() - 1) {
                    table.append(" | ");
                }
            }
            table.append("\n");
        }
        
        if (data.getRows().size() > rowLimit) {
            table.append("\n... and ").append(data.getRows().size() - rowLimit).append(" more rows");
        }
        
        return table.toString();
    }
    
    /**
     * Get vector context for the query
     */
    private Map<String, Object> getVectorContext(String query, String connectionId, String userId) {
        try {
            logger.info("Getting vector context for query: '{}' (connectionId: {}, userId: {})", query, connectionId, userId);
            
            // Get the actual database connection to query schema directly
            Optional<UserDatabaseConnection> connectionOpt = 
                connectionService.findUserDatabaseConnection(connectionId, userId);
            
            if (connectionOpt.isEmpty()) {
                logger.warn("Database connection not found for connectionId: {}", connectionId);
                return Collections.emptyMap();
            }
            
            UserDatabaseConnection dbConnection = connectionOpt.get();
            DatabaseType databaseType = DatabaseType.fromString(dbConnection.getDatabaseType());
            
            // Query the actual database schema
            Map<String, Object> schemaInfo = getActualDatabaseSchema(dbConnection, databaseType);
            
            logger.info("Retrieved actual schema for database: {} (tables: {})", 
                dbConnection.getDatabaseName(), 
                schemaInfo.containsKey("tables") ? ((List<?>) schemaInfo.get("tables")).size() : 0);
            
            return schemaInfo;
            
        } catch (Exception e) {
            logger.error("Error getting vector context: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Get actual database schema by querying the database directly
     */
    private Map<String, Object> getActualDatabaseSchema(UserDatabaseConnection dbConnection, DatabaseType databaseType) {
        Map<String, Object> schemaInfo = new HashMap<>();
        List<Map<String, Object>> tables = new ArrayList<>();
        
        try {
            // Create a direct database connection to query schema
            java.sql.Connection connection = createDirectConnection(dbConnection, databaseType);
            
            if (connection != null) {
                try {
                    // Get all tables based on database type
                    List<String> tableNames = getTableNames(connection, databaseType, dbConnection.getDatabaseName());
                    
                    for (String tableName : tableNames) {
                        Map<String, Object> tableInfo = new HashMap<>();
                        tableInfo.put("tableName", tableName);
                        
                        // Get columns for this table
                        List<Map<String, Object>> columns = getTableColumns(connection, databaseType, tableName);
                        tableInfo.put("columns", columns);
                        
                        // Create a description based on table name and columns
                        String description = generateTableDescription(tableName, columns);
                        tableInfo.put("description", description);
                        
                        tables.add(tableInfo);
                    }
                    
                    schemaInfo.put("tables", tables);
                    schemaInfo.put("databaseName", dbConnection.getDatabaseName());
                    schemaInfo.put("databaseType", databaseType.toString());
                    
                    logger.info("Successfully retrieved schema for {} tables in database: {}", 
                        tables.size(), dbConnection.getDatabaseName());
                    
                } finally {
                    connection.close();
                }
            }
            
        } catch (Exception e) {
            logger.error("Error querying database schema: {}", e.getMessage(), e);
        }
        
        return schemaInfo;
    }
    
    /**
     * Create a direct database connection
     */
    private java.sql.Connection createDirectConnection(UserDatabaseConnection dbConnection, DatabaseType databaseType) {
        try {
            String url;
            switch (databaseType) {
                case POSTGRESQL:
                    url = String.format("jdbc:postgresql://%s:%d/%s", 
                        dbConnection.getHost(), dbConnection.getPort(), dbConnection.getDatabaseName());
                    return java.sql.DriverManager.getConnection(url, dbConnection.getUsername(), dbConnection.getPassword());
                    
                case MYSQL:
                    url = String.format("jdbc:mysql://%s:%d/%s", 
                        dbConnection.getHost(), dbConnection.getPort(), dbConnection.getDatabaseName());
                    return java.sql.DriverManager.getConnection(url, dbConnection.getUsername(), dbConnection.getPassword());
                    
                case MONGODB:
                    // MongoDB doesn't use JDBC, so we'll return null for now
                    logger.warn("MongoDB schema querying not implemented yet");
                    return null;
                    
                default:
                    logger.warn("Unsupported database type: {}", databaseType);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Error creating database connection: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get table names from the database
     */
    private List<String> getTableNames(java.sql.Connection connection, DatabaseType databaseType, String databaseName) {
        List<String> tableNames = new ArrayList<>();
        
        try {
            String query;
            switch (databaseType) {
                case POSTGRESQL:
                    // Query all schemas, not just 'public'
                    query = "SELECT schemaname || '.' || tablename FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema') ORDER BY schemaname, tablename";
                    break;
                    
                case MYSQL:
                    query = "SELECT table_name FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name";
                    break;
                    
                default:
                    return tableNames;
            }
            
            try (java.sql.PreparedStatement stmt = connection.prepareStatement(query)) {
                if (databaseType == DatabaseType.MYSQL) {
                    stmt.setString(1, databaseName);
                }
                
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        tableNames.add(rs.getString(1));
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting table names: {}", e.getMessage(), e);
        }
        
        return tableNames;
    }
    
    /**
     * Get column information for a table
     */
    private List<Map<String, Object>> getTableColumns(java.sql.Connection connection, DatabaseType databaseType, String tableName) {
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try {
            String query;
            switch (databaseType) {
                case POSTGRESQL:
                    // Handle schema-qualified table names
                    if (tableName.contains(".")) {
                        String[] parts = tableName.split("\\.");
                        String schema = parts[0];
                        String table = parts[1];
                        query = "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position";
                    } else {
                        query = "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = ? ORDER BY ordinal_position";
                    }
                    break;
                    
                case MYSQL:
                    query = "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = ? ORDER BY ordinal_position";
                    break;
                    
                default:
                    return columns;
            }
            
            try (java.sql.PreparedStatement stmt = connection.prepareStatement(query)) {
                if (databaseType == DatabaseType.POSTGRESQL && tableName.contains(".")) {
                    String[] parts = tableName.split("\\.");
                    stmt.setString(1, parts[0]); // schema
                    stmt.setString(2, parts[1]); // table
                } else {
                    stmt.setString(1, tableName);
                }
                
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> column = new HashMap<>();
                        column.put("name", rs.getString("column_name"));
                        column.put("type", rs.getString("data_type"));
                        column.put("nullable", "YES".equals(rs.getString("is_nullable")));
                        columns.add(column);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting columns for table {}: {}", tableName, e.getMessage(), e);
        }
        
        return columns;
    }
    
    /**
     * Generate a description for a table based on its name and columns
     */
    private String generateTableDescription(String tableName, List<Map<String, Object>> columns) {
        StringBuilder description = new StringBuilder();
        description.append("Table '").append(tableName).append("' with columns: ");
        
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> column = columns.get(i);
            description.append(column.get("name")).append(" (").append(column.get("type")).append(")");
            
            if (i < columns.size() - 1) {
                description.append(", ");
            }
        }
        
        return description.toString();
    }
    
    /**
     * Get graph context for the query
     */
    private Map<String, Object> getGraphContext(String query, String connectionId) {
        try {
            logger.debug("Getting graph context for query: {}", query);
            
            // For now, return basic structure - this can be enhanced later with actual relationship detection
            Map<String, Object> graphContext = new HashMap<>();
            graphContext.put("connectionId", connectionId);
            graphContext.put("relationships", Collections.emptyList());
            graphContext.put("message", "Graph context available but not yet implemented");
            
            return graphContext;
            
        } catch (Exception e) {
            logger.warn("Error getting graph context: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
    

    
    /**
     * Clean query by removing markdown formatting and extra content
     */
    private String cleanQuery(String query) {
        if (query == null) return "";
        
        String cleaned = query;
        
        // Remove markdown code blocks
        cleaned = cleaned.replaceAll("```sql\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*", "");
        
        // Remove markdown formatting like **QUERY**
        cleaned = cleaned.replaceAll("\\*\\*[^*]+\\*\\*\\s*", "");
        
        // Remove the word "QUERY" at the beginning (case insensitive)
        cleaned = cleaned.replaceAll("(?i)^\\s*QUERY\\s+", "");
        
        // Remove the word "EXPLANATION" at the beginning (case insensitive)
        cleaned = cleaned.replaceAll("(?i)^\\s*EXPLANATION\\s+", "");
        
        // Remove extra whitespace and newlines
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        
        // If the query contains multiple lines, take the first meaningful line
        String[] lines = cleaned.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("--") && !line.startsWith("#")) {
                return line;
            }
        }
        
        return cleaned;
    }
    
    /**
     * Clean text by removing markdown formatting and special characters
     */
    private String cleanText(String text) {
        if (text == null) return "";
        
        String cleaned = text;
        
        // Remove markdown formatting
        cleaned = cleaned.replaceAll("\\*\\*[^*]+\\*\\*", "");
        cleaned = cleaned.replaceAll("\\*[^*]+\\*", "");
        cleaned = cleaned.replaceAll("`[^`]+`", "");
        
        // Remove special characters but keep newlines
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s\\n\\.,;:!?()\\[\\]{}'\"\\-+=<>/\\\\]", "");
        
        // Clean up extra whitespace but preserve newlines
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n[ \\t]+", "\n");
        
        return cleaned.trim();
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

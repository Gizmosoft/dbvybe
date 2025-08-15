package com.dbVybe.app.service.agent;

import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.service.ActorServiceLocator;
import com.dbVybe.app.service.UserDatabaseConnectionService;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.math.BigDecimal;

// MongoDB imports
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.FindIterable;
import org.bson.Document;
import org.bson.json.JsonParseException;
import com.mongodb.MongoException;

/**
 * Query Execution Agent - Safely executes database queries with multi-database support
 * 
 * This agent:
 * - Executes SELECT queries on MySQL, PostgreSQL, and MongoDB
 * - Validates query safety (blocks UPDATE, DELETE, DROP, etc.)
 * - Provides extensible architecture for new database types
 * - Returns formatted query results with proper error handling
 * - Integrates with existing database connection management
 */
@Service
public class QueryExecutionAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutionAgent.class);
    
    // Query Safety Patterns - Dangerous operations to block
    private static final List<Pattern> DANGEROUS_QUERY_PATTERNS = Arrays.asList(
        // SQL dangerous operations
        Pattern.compile("(?i)\\b(UPDATE|DELETE|DROP|ALTER|CREATE|INSERT|TRUNCATE|REPLACE)\\b"),
        Pattern.compile("(?i)\\b(GRANT|REVOKE|FLUSH|RESET|SHUTDOWN)\\b"),
        Pattern.compile("(?i)\\b(LOAD\\s+DATA|INTO\\s+OUTFILE|LOAD_FILE)\\b"),
        Pattern.compile("(?i)\\b(CALL|EXECUTE|EXEC)\\b"),
        Pattern.compile("(?i)--\\s*[^\\r\\n]*"), // SQL comments that might hide dangerous operations
        Pattern.compile("(?i)/\\*.*?\\*/", Pattern.DOTALL), // Block comments
        
        // MongoDB dangerous operations
        Pattern.compile("(?i)\"(insert|update|delete|remove|drop|create)\"\\s*:"),
        Pattern.compile("(?i)\"(insertOne|insertMany|updateOne|updateMany|deleteOne|deleteMany|replaceOne)\"\\s*:"),
        Pattern.compile("(?i)\"(dropDatabase|dropCollection|createCollection|createIndex)\"\\s*:"),
        Pattern.compile("(?i)\\$eval"), // Dangerous $eval operator
        Pattern.compile("(?i)\\$where") // Dangerous $where operator
    );
    
    // Allowed query patterns - Only SELECT operations and MongoDB find operations
    private static final List<Pattern> SAFE_QUERY_PATTERNS = Arrays.asList(
        // SQL patterns
        Pattern.compile("(?i)^\\s*SELECT\\b.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*SHOW\\b.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*DESCRIBE\\b.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*EXPLAIN\\b.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*WITH\\b.*SELECT\\b.*", Pattern.DOTALL), // CTE queries
        
        // MongoDB patterns (JSON-based find operations)
        Pattern.compile("(?i)^\\s*\\{.*\"find\"\\s*:.*\\}.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*\\{.*\"aggregate\"\\s*:.*\\}.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*\\{.*\"count\"\\s*:.*\\}.*", Pattern.DOTALL),
        Pattern.compile("(?i)^\\s*\\{.*\"distinct\"\\s*:.*\\}.*", Pattern.DOTALL)
    );
    
    // Execution Statistics
    private long totalQueriesExecuted = 0;
    private long totalQueriesBlocked = 0;
    private long totalErrors = 0;
    private final Map<DatabaseType, Long> executionsByType = new HashMap<>();
    
    /**
     * Execute a database query safely
     */
    public CompletableFuture<QueryExecutionResult> executeQuery(
            String query, 
            String connectionId, 
            String userId, 
            int maxRows) {
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                logger.info("Executing query for user {} on connection {} (max rows: {})", userId, connectionId, maxRows);
                logger.debug("Query to execute: {}", query);
                
                // 1. Validate query safety
                QuerySafetyResult safetyResult = validateQuerySafety(query);
                if (!safetyResult.isSafe()) {
                    totalQueriesBlocked++;
                    logger.warn("Blocked unsafe query for user {}: {}", userId, safetyResult.getReason());
                    return new QueryExecutionResult(
                        false, 
                        safetyResult.getReason(),
                        null,
                        0,
                        System.currentTimeMillis() - startTime,
                        "BLOCKED"
                    );
                }
                
                // 2. Get database connection information
                UserDatabaseConnection dbConnection = getConnectionInfo(connectionId, userId);
                if (dbConnection == null) {
                    return new QueryExecutionResult(
                        false,
                        "Database connection not found or not accessible",
                        null,
                        0,
                        System.currentTimeMillis() - startTime,
                        "CONNECTION_ERROR"
                    );
                }
                
                // 3. Execute query based on database type
                DatabaseType dbType = DatabaseType.fromString(dbConnection.getDatabaseType());
                QueryExecutionResult result = executeQueryByType(query, dbConnection, dbType, maxRows, startTime);
                
                // 4. Update statistics
                totalQueriesExecuted++;
                executionsByType.merge(dbType, 1L, Long::sum);
                
                if (!result.isSuccess()) {
                    totalErrors++;
                }
                
                logger.info("Query execution completed for user {} - Success: {}, Rows: {}, Time: {}ms", 
                    userId, result.isSuccess(), result.getRowCount(), result.getExecutionTimeMs());
                
                return result;
                
            } catch (Exception e) {
                totalErrors++;
                logger.error("Error executing query for user {}: {}", userId, e.getMessage(), e);
                return new QueryExecutionResult(
                    false,
                    "Query execution failed: " + e.getMessage(),
                    null,
                    0,
                    System.currentTimeMillis() - startTime,
                    "EXECUTION_ERROR"
                );
            }
        });
    }
    
    /**
     * Get supported database types
     */
    public List<DatabaseType> getSupportedDatabaseTypes() {
        return Arrays.asList(DatabaseType.MYSQL, DatabaseType.POSTGRESQL, DatabaseType.MONGODB);
    }
    
    /**
     * Get execution statistics
     */
    public QueryExecutionStats getStats() {
        return new QueryExecutionStats(
            totalQueriesExecuted,
            totalQueriesBlocked,
            totalErrors,
            new HashMap<>(executionsByType)
        );
    }
    
    // Helper Methods
    
    private QuerySafetyResult validateQuerySafety(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new QuerySafetyResult(false, "Empty query not allowed");
        }
        
        String trimmedQuery = query.trim();
        
        // Check if the query is actually SQL (not explanatory text)
        if (!isValidSQLQuery(trimmedQuery)) {
            return new QuerySafetyResult(false, 
                "Query appears to be explanatory text, not valid SQL. Please generate an actual SQL query.");
        }
        
        // Check for dangerous patterns (keeping only the most critical ones)
        // Removed most restrictions to allow all query types
        for (Pattern dangerousPattern : DANGEROUS_QUERY_PATTERNS) {
            if (dangerousPattern.matcher(trimmedQuery).find()) {
                return new QuerySafetyResult(false, 
                    "Query contains dangerous operations (UPDATE/DELETE/DROP/etc.) which are not allowed");
            }
        }
        
        // REMOVED: SAFE_QUERY_PATTERNS check - now allowing all query types
        // All query types are now allowed as long as they don't contain dangerous patterns
        
        // Additional safety checks
        if (trimmedQuery.contains(";") && trimmedQuery.indexOf(";") < trimmedQuery.length() - 1) {
            return new QuerySafetyResult(false, 
                "Multiple statements not allowed for security reasons");
        }
        
        return new QuerySafetyResult(true, null);
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
    
    private UserDatabaseConnection getConnectionInfo(String connectionId, String userId) {
        try {
            UserDatabaseConnectionService connectionService = ActorServiceLocator.getUserDatabaseConnectionService();
            if (connectionService != null) {
                Optional<UserDatabaseConnection> connection = 
                    connectionService.findUserDatabaseConnection(connectionId, userId);
                return connection.orElse(null);
            }
        } catch (Exception e) {
            logger.error("Error retrieving connection info: {}", e.getMessage(), e);
        }
        return null;
    }
    
    private QueryExecutionResult executeQueryByType(
            String query, 
            UserDatabaseConnection dbConnection, 
            DatabaseType dbType, 
            int maxRows,
            long startTime) {
        
        switch (dbType) {
            case MYSQL:
            case POSTGRESQL:
                return executeSQLQuery(query, dbConnection, dbType, maxRows, startTime);
            case MONGODB:
                return executeMongoQuery(query, dbConnection, maxRows, startTime);
            default:
                return new QueryExecutionResult(
                    false,
                    "Unsupported database type: " + dbType,
                    null,
                    0,
                    System.currentTimeMillis() - startTime,
                    "UNSUPPORTED_DB_TYPE"
                );
        }
    }
    
    private QueryExecutionResult executeSQLQuery(
            String query, 
            UserDatabaseConnection dbConnection, 
            DatabaseType dbType, 
            int maxRows,
            long startTime) {
        
        String jdbcUrl = buildJdbcUrl(dbConnection, dbType);
        
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, 
                dbConnection.getUsername(), 
                dbConnection.getPassword())) {
            
            // Handle parameterized queries by converting them to direct SQL
            String processedQuery = processParameterizedQuery(query);
            logger.debug("Original query: {}", query);
            logger.debug("Processed query: {}", processedQuery);
            
            try (PreparedStatement stmt = connection.prepareStatement(processedQuery)) {
                stmt.setMaxRows(maxRows);
                stmt.setQueryTimeout(30); // 30 second timeout
                
                try (ResultSet rs = stmt.executeQuery()) {
                    QueryResultData resultData = convertResultSetToData(rs, maxRows);
                    
                    return new QueryExecutionResult(
                        true,
                        null,
                        resultData,
                        resultData.getRows().size(),
                        System.currentTimeMillis() - startTime,
                        "SUCCESS"
                    );
                }
            }
            
        } catch (SQLException e) {
            logger.error("SQL execution error: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "SQL Error: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "SQL_ERROR"
            );
        }
    }
    
    /**
     * Process parameterized queries by replacing placeholders with default values
     */
    private String processParameterizedQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }
        
        String processedQuery = query;
        
        // Replace PostgreSQL-style parameters ($1, $2, etc.) with default values
        int paramIndex = 1;
        while (processedQuery.contains("$" + paramIndex)) {
            // Use a default value based on context - this is a simplified approach
            // In a real implementation, you'd want to extract actual values from the user's request
            String defaultValue = getDefaultParameterValue(paramIndex, query);
            processedQuery = processedQuery.replace("$" + paramIndex, defaultValue);
            paramIndex++;
        }
        
        // Replace MySQL-style parameters (?) with default values
        paramIndex = 1;
        while (processedQuery.contains("?")) {
            String defaultValue = getDefaultParameterValue(paramIndex, query);
            processedQuery = processedQuery.replaceFirst("\\?", defaultValue);
            paramIndex++;
        }
        
        return processedQuery;
    }
    
    /**
     * Get a default parameter value based on the query context
     */
    private String getDefaultParameterValue(int paramIndex, String query) {
        // This is a simplified approach - in a real implementation, you'd want to
        // extract actual values from the user's request or use a more sophisticated approach
        
        // Try to infer the type from the query context
        String lowerQuery = query.toLowerCase();
        
        if (lowerQuery.contains("amount") || lowerQuery.contains("price") || lowerQuery.contains("cost")) {
            return "0"; // Default numeric value for amounts
        } else if (lowerQuery.contains("name") || lowerQuery.contains("title") || lowerQuery.contains("description")) {
            return "'default'"; // Default string value
        } else if (lowerQuery.contains("date") || lowerQuery.contains("created") || lowerQuery.contains("updated")) {
            return "'2024-01-01'"; // Default date value
        } else if (lowerQuery.contains("id")) {
            return "1"; // Default ID value
        } else {
            // Generic default based on parameter position
            return paramIndex % 2 == 0 ? "'value" + paramIndex + "'" : String.valueOf(paramIndex);
        }
    }
    
    private QueryExecutionResult executeMongoQuery(
            String query, 
            UserDatabaseConnection dbConnection, 
            int maxRows,
            long startTime) {
        
        String mongoUri = buildMongoUri(dbConnection);
        
        try (MongoClient mongoClient = MongoClients.create(mongoUri)) {
            MongoDatabase database = mongoClient.getDatabase(dbConnection.getDatabaseName());
            
            logger.info("Executing MongoDB query on database: {}", dbConnection.getDatabaseName());
            
            // Parse the MongoDB query (expecting JSON format)
            Document queryDoc;
            try {
                queryDoc = Document.parse(query);
            } catch (JsonParseException e) {
                logger.error("Invalid JSON query format: {}", e.getMessage());
                return new QueryExecutionResult(
                    false,
                    "Invalid MongoDB query format. Expected JSON format like: {\"find\": \"collection\", \"filter\": {...}}",
                    null,
                    0,
                    System.currentTimeMillis() - startTime,
                    "INVALID_FORMAT"
                );
            }
            
            // Execute based on operation type
            if (queryDoc.containsKey("find")) {
                return executeMongoFind(database, queryDoc, maxRows, startTime);
            } else if (queryDoc.containsKey("aggregate")) {
                return executeMongoAggregate(database, queryDoc, maxRows, startTime);
            } else if (queryDoc.containsKey("count")) {
                return executeMongoCount(database, queryDoc, startTime);
            } else if (queryDoc.containsKey("distinct")) {
                return executeMongoDistinct(database, queryDoc, maxRows, startTime);
            } else {
                return new QueryExecutionResult(
                    false,
                    "Unsupported MongoDB operation. Only find, aggregate, count, and distinct operations are allowed.",
                    null,
                    0,
                    System.currentTimeMillis() - startTime,
                    "UNSUPPORTED_OPERATION"
                );
            }
            
        } catch (MongoException e) {
            logger.error("MongoDB execution error: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "MongoDB Error: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "MONGO_ERROR"
            );
        } catch (Exception e) {
            logger.error("Unexpected error during MongoDB query execution: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "MongoDB execution failed: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "EXECUTION_ERROR"
            );
        }
    }
    
    private String buildJdbcUrl(UserDatabaseConnection dbConnection, DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    dbConnection.getHost(),
                    dbConnection.getPort(),
                    dbConnection.getDatabaseName());
            case POSTGRESQL:
                return String.format("jdbc:postgresql://%s:%d/%s",
                    dbConnection.getHost(),
                    dbConnection.getPort(),
                    dbConnection.getDatabaseName());
            default:
                throw new IllegalArgumentException("Unsupported database type for JDBC: " + dbType);
        }
    }
    
    private String buildMongoUri(UserDatabaseConnection dbConnection) {
        // Build MongoDB connection URI
        if (dbConnection.getUsername() != null && !dbConnection.getUsername().trim().isEmpty()) {
            return String.format("mongodb://%s:%s@%s:%d/%s",
                dbConnection.getUsername(),
                dbConnection.getPassword(),
                dbConnection.getHost(),
                dbConnection.getPort(),
                dbConnection.getDatabaseName());
        } else {
            return String.format("mongodb://%s:%d/%s",
                dbConnection.getHost(),
                dbConnection.getPort(),
                dbConnection.getDatabaseName());
        }
    }
    
    private QueryExecutionResult executeMongoFind(MongoDatabase database, Document queryDoc, int maxRows, long startTime) {
        try {
            String collectionName = queryDoc.getString("find");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            Document filter = queryDoc.get("filter", new Document());
            Document projection = queryDoc.get("projection", new Document());
            Document sort = queryDoc.get("sort", new Document());
            int skip = queryDoc.getInteger("skip", 0);
            int limit = Math.min(queryDoc.getInteger("limit", maxRows), maxRows);
            
            FindIterable<Document> findIterable = collection.find(filter)
                .projection(projection.isEmpty() ? null : projection)
                .sort(sort.isEmpty() ? null : sort)
                .skip(skip)
                .limit(limit);
            
            return convertMongoResultToQueryResult(findIterable, startTime);
            
        } catch (Exception e) {
            logger.error("Error executing MongoDB find: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "MongoDB find error: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "MONGO_FIND_ERROR"
            );
        }
    }
    
    private QueryExecutionResult executeMongoAggregate(MongoDatabase database, Document queryDoc, int maxRows, long startTime) {
        try {
            String collectionName = queryDoc.getString("aggregate");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            List<Document> pipeline = queryDoc.getList("pipeline", Document.class);
            if (pipeline == null || pipeline.isEmpty()) {
                return new QueryExecutionResult(
                    false,
                    "Aggregation pipeline is required",
                    null,
                    0,
                    System.currentTimeMillis() - startTime,
                    "INVALID_PIPELINE"
                );
            }
            
            // Add limit to pipeline if not present
            boolean hasLimit = pipeline.stream().anyMatch(stage -> stage.containsKey("$limit"));
            if (!hasLimit) {
                pipeline.add(new Document("$limit", maxRows));
            }
            
            return convertMongoResultToQueryResult(collection.aggregate(pipeline), startTime);
            
        } catch (Exception e) {
            logger.error("Error executing MongoDB aggregate: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "MongoDB aggregate error: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "MONGO_AGGREGATE_ERROR"
            );
        }
    }
    
    private QueryExecutionResult executeMongoCount(MongoDatabase database, Document queryDoc, long startTime) {
        try {
            String collectionName = queryDoc.getString("count");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            Document filter = queryDoc.get("query", new Document());
            long count = collection.countDocuments(filter);
            
            // Create result data for count
            List<String> columnNames = Arrays.asList("count");
            List<String> columnTypes = Arrays.asList("LONG");
            List<List<Object>> rows = Arrays.asList(Arrays.asList((Object) count));
            
            QueryResultData resultData = new QueryResultData(columnNames, columnTypes, rows);
            
            return new QueryExecutionResult(
                true,
                null,
                resultData,
                1,
                System.currentTimeMillis() - startTime,
                "SUCCESS"
            );
            
        } catch (Exception e) {
            logger.error("Error executing MongoDB count: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "MongoDB count error: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "MONGO_COUNT_ERROR"
            );
        }
    }
    
    private QueryExecutionResult executeMongoDistinct(MongoDatabase database, Document queryDoc, int maxRows, long startTime) {
        try {
            String collectionName = queryDoc.getString("distinct");
            String field = queryDoc.getString("key");
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            Document filter = queryDoc.get("query", new Document());
            
            List<Object> distinctValues = new ArrayList<>();
            int count = 0;
            for (Object value : collection.distinct(field, filter, Object.class)) {
                if (count >= maxRows) break;
                distinctValues.add(value);
                count++;
            }
            
            // Create result data for distinct values
            List<String> columnNames = Arrays.asList(field);
            List<String> columnTypes = Arrays.asList("OBJECT");
            List<List<Object>> rows = distinctValues.stream()
                .map(value -> Arrays.asList((Object) (value != null ? value.toString() : null)))
                .collect(java.util.stream.Collectors.toList());
            
            QueryResultData resultData = new QueryResultData(columnNames, columnTypes, rows);
            
            return new QueryExecutionResult(
                true,
                null,
                resultData,
                rows.size(),
                System.currentTimeMillis() - startTime,
                "SUCCESS"
            );
            
        } catch (Exception e) {
            logger.error("Error executing MongoDB distinct: {}", e.getMessage(), e);
            return new QueryExecutionResult(
                false,
                "MongoDB distinct error: " + e.getMessage(),
                null,
                0,
                System.currentTimeMillis() - startTime,
                "MONGO_DISTINCT_ERROR"
            );
        }
    }
    
    private QueryExecutionResult convertMongoResultToQueryResult(Iterable<Document> results, long startTime) {
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        
        boolean headerProcessed = false;
        
        for (Document doc : results) {
            if (!headerProcessed) {
                // Extract column names from the first document
                for (String key : doc.keySet()) {
                    columnNames.add(key);
                    columnTypes.add("OBJECT"); // MongoDB fields can be of any type
                }
                headerProcessed = true;
            }
            
            List<Object> row = new ArrayList<>();
            for (String columnName : columnNames) {
                Object value = doc.get(columnName);
                
                // Convert MongoDB types to serializable formats
                if (value instanceof org.bson.types.ObjectId) {
                    value = value.toString();
                } else if (value instanceof java.util.Date) {
                    value = value.toString();
                } else if (value instanceof Document) {
                    value = ((Document) value).toJson();
                } else if (value instanceof List) {
                    value = value.toString();
                }
                
                row.add(value);
            }
            rows.add(row);
        }
        
        QueryResultData resultData = new QueryResultData(columnNames, columnTypes, rows);
        
        return new QueryExecutionResult(
            true,
            null,
            resultData,
            rows.size(),
            System.currentTimeMillis() - startTime,
            "SUCCESS"
        );
    }
    
    private QueryResultData convertResultSetToData(ResultSet rs, int maxRows) throws SQLException {
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        
        // Get column metadata
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metaData.getColumnName(i));
            columnTypes.add(metaData.getColumnTypeName(i));
        }
        
        // Get row data
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            List<Object> row = new ArrayList<>();
            
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                
                // Convert special types to serializable formats
                if (value instanceof Timestamp) {
                    value = ((Timestamp) value).toLocalDateTime().toString();
                } else if (value instanceof java.sql.Date) {
                    value = value.toString();
                } else if (value instanceof BigDecimal) {
                    value = ((BigDecimal) value).toString();
                } else if (value instanceof Clob) {
                    Clob clob = (Clob) value;
                    value = clob.getSubString(1, (int) clob.length());
                } else if (value instanceof Blob) {
                    value = "[BLOB DATA]";
                }
                
                row.add(value);
            }
            
            rows.add(row);
            rowCount++;
        }
        
        return new QueryResultData(columnNames, columnTypes, rows);
    }
    
    // Data Classes
    
    public static class QueryExecutionResult {
        private final boolean success;
        private final String error;
        private final QueryResultData data;
        private final int rowCount;
        private final long executionTimeMs;
        private final String status;
        
        public QueryExecutionResult(boolean success, String error, QueryResultData data, 
                                  int rowCount, long executionTimeMs, String status) {
            this.success = success;
            this.error = error;
            this.data = data;
            this.rowCount = rowCount;
            this.executionTimeMs = executionTimeMs;
            this.status = status;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public QueryResultData getData() { return data; }
        public int getRowCount() { return rowCount; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getStatus() { return status; }
    }
    
    public static class QueryResultData {
        private final List<String> columnNames;
        private final List<String> columnTypes;
        private final List<List<Object>> rows;
        
        public QueryResultData(List<String> columnNames, List<String> columnTypes, List<List<Object>> rows) {
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.rows = rows;
        }
        
        public List<String> getColumnNames() { return columnNames; }
        public List<String> getColumnTypes() { return columnTypes; }
        public List<List<Object>> getRows() { return rows; }
    }
    
    public static class QuerySafetyResult {
        private final boolean safe;
        private final String reason;
        
        public QuerySafetyResult(boolean safe, String reason) {
            this.safe = safe;
            this.reason = reason;
        }
        
        public boolean isSafe() { return safe; }
        public String getReason() { return reason; }
    }
    
    public static class QueryExecutionStats {
        private final long totalQueriesExecuted;
        private final long totalQueriesBlocked;
        private final long totalErrors;
        private final Map<DatabaseType, Long> executionsByType;
        
        public QueryExecutionStats(long totalQueriesExecuted, long totalQueriesBlocked, 
                                 long totalErrors, Map<DatabaseType, Long> executionsByType) {
            this.totalQueriesExecuted = totalQueriesExecuted;
            this.totalQueriesBlocked = totalQueriesBlocked;
            this.totalErrors = totalErrors;
            this.executionsByType = executionsByType;
        }
        
        public long getTotalQueriesExecuted() { return totalQueriesExecuted; }
        public long getTotalQueriesBlocked() { return totalQueriesBlocked; }
        public long getTotalErrors() { return totalErrors; }
        public Map<DatabaseType, Long> getExecutionsByType() { return executionsByType; }
    }
}

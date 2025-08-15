package com.dbVybe.app.actor.analysis;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.service.agent.DatabaseSchemaAgent;
import com.dbVybe.app.service.ActorServiceLocator;
import com.dbVybe.app.service.UserDatabaseConnectionService;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SchemaAnalysisActor - Responsible for analyzing database schemas
 * 
 * This actor:
 * - Receives schema analysis requests when new databases are connected
 * - Uses DatabaseSchemaAgent to analyze schema structure
 * - Generates vector embeddings and stores them in Qdrant
 * - Provides schema insights and similarity search capabilities
 */
public class SchemaAnalysisActor extends AbstractBehavior<SchemaAnalysisActor.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(SchemaAnalysisActor.class);
    
    private final DatabaseSchemaAgent schemaAgent;
    private final AtomicLong activeAnalysisRequests = new AtomicLong(0);
    private final AtomicLong totalAnalysisRequests = new AtomicLong(0);
    
    // Actor Commands
    public interface Command {}
    
    /**
     * Analyze schema for a newly connected database
     */
    public static class AnalyzeSchema implements Command {
        private final String connectionId;
        private final String userId;
        private final DatabaseType databaseType;
        private final ActorRef<SchemaAnalysisResponse> replyTo;
        
        public AnalyzeSchema(String connectionId, String userId, DatabaseType databaseType, 
                           ActorRef<SchemaAnalysisResponse> replyTo) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.databaseType = databaseType;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public ActorRef<SchemaAnalysisResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Search for similar schemas using vector similarity
     */
    public static class SearchSimilarSchemas implements Command {
        private final String query;
        private final int limit;
        private final String userId;
        private final String connectionId; // Add connectionId parameter
        private final ActorRef<SimilarSchemasResponse> replyTo;
        
        public SearchSimilarSchemas(String query, int limit, String userId, 
                                  ActorRef<SimilarSchemasResponse> replyTo) {
            this(query, limit, userId, null, replyTo);
        }
        
        public SearchSimilarSchemas(String query, int limit, String userId, String connectionId,
                                  ActorRef<SimilarSchemasResponse> replyTo) {
            this.query = query;
            this.limit = limit;
            this.userId = userId;
            this.connectionId = connectionId;
            this.replyTo = replyTo;
        }
        
        public String getQuery() { return query; }
        public int getLimit() { return limit; }
        public String getUserId() { return userId; }
        public String getConnectionId() { return connectionId; }
        public ActorRef<SimilarSchemasResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get schema analysis statistics
     */
    public static class GetAnalysisStats implements Command {
        private final ActorRef<AnalysisStatsResponse> replyTo;
        
        public GetAnalysisStats(ActorRef<AnalysisStatsResponse> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<AnalysisStatsResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Response for schema analysis
     */
    public static class SchemaAnalysisResponse {
        private final String connectionId;
        private final String userId;
        private final boolean success;
        private final String message;
        private final int tablesAnalyzed;
        private final int embeddingsGenerated;
        private final long processingTimeMs;
        private final String error;
        
        public SchemaAnalysisResponse(String connectionId, String userId, boolean success, String message,
                                    int tablesAnalyzed, int embeddingsGenerated, long processingTimeMs, String error) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.success = success;
            this.message = message;
            this.tablesAnalyzed = tablesAnalyzed;
            this.embeddingsGenerated = embeddingsGenerated;
            this.processingTimeMs = processingTimeMs;
            this.error = error;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getTablesAnalyzed() { return tablesAnalyzed; }
        public int getEmbeddingsGenerated() { return embeddingsGenerated; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public String getError() { return error; }
    }
    
    /**
     * Response for similar schemas search
     */
    public static class SimilarSchemasResponse {
        private final java.util.List<DatabaseSchemaAgent.SimilarSchema> similarSchemas;
        private final boolean success;
        private final String error;
        
        public SimilarSchemasResponse(java.util.List<DatabaseSchemaAgent.SimilarSchema> similarSchemas, 
                                    boolean success, String error) {
            this.similarSchemas = similarSchemas;
            this.success = success;
            this.error = error;
        }
        
        public java.util.List<DatabaseSchemaAgent.SimilarSchema> getSimilarSchemas() { return similarSchemas; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    /**
     * Response for analysis statistics
     */
    public static class AnalysisStatsResponse {
        private final long activeRequests;
        private final long totalRequests;
        private final DatabaseSchemaAgent.SchemaAgentStats agentStats;
        private final boolean success;
        
        public AnalysisStatsResponse(long activeRequests, long totalRequests, 
                                   DatabaseSchemaAgent.SchemaAgentStats agentStats, boolean success) {
            this.activeRequests = activeRequests;
            this.totalRequests = totalRequests;
            this.agentStats = agentStats;
            this.success = success;
        }
        
        public long getActiveRequests() { return activeRequests; }
        public long getTotalRequests() { return totalRequests; }
        public DatabaseSchemaAgent.SchemaAgentStats getAgentStats() { return agentStats; }
        public boolean isSuccess() { return success; }
    }
    
    public static Behavior<Command> create(DatabaseSchemaAgent schemaAgent) {
        return Behaviors.setup(context -> new SchemaAnalysisActor(context, schemaAgent));
    }
    
    private SchemaAnalysisActor(ActorContext<Command> context, DatabaseSchemaAgent schemaAgent) {
        super(context);
        this.schemaAgent = schemaAgent;
        logger.info("SchemaAnalysisActor created and ready to analyze database schemas");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AnalyzeSchema.class, this::onAnalyzeSchema)
            .onMessage(SearchSimilarSchemas.class, this::onSearchSimilarSchemas)
            .onMessage(GetAnalysisStats.class, this::onGetAnalysisStats)
            .build();
    }
    
    private Behavior<Command> onAnalyzeSchema(AnalyzeSchema command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeAnalysisRequests.incrementAndGet();
        totalAnalysisRequests.incrementAndGet();
        
        logger.info("Starting schema analysis for connection {} (user: {}, type: {}) - Request ID: {}", 
            command.getConnectionId(), command.getUserId(), command.getDatabaseType(), requestId);
        
        // Perform schema analysis asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                // Get connection details from database
                UserDatabaseConnectionService connectionService = ActorServiceLocator.getUserDatabaseConnectionService();
                if (connectionService == null) {
                    throw new RuntimeException("UserDatabaseConnectionService not available");
                }
                
                Optional<UserDatabaseConnection> connectionOpt = 
                    connectionService.findUserDatabaseConnection(command.getConnectionId(), command.getUserId());
                
                if (connectionOpt.isEmpty()) {
                    throw new RuntimeException("Database connection not found: " + command.getConnectionId());
                }
                
                UserDatabaseConnection dbConnection = connectionOpt.get();
                
                // Create database connection based on database type
                Connection connection = null;
                if (command.getDatabaseType() != DatabaseType.MONGODB) {
                    connection = createDatabaseConnection(dbConnection);
                }
                
                try {
                    // Analyze schema using the agent
                    DatabaseSchemaAgent.SchemaAnalysisResult result = 
                        schemaAgent.analyzeSchema(
                            command.getConnectionId(),
                            command.getUserId(),
                            command.getDatabaseType(),
                            connection
                        ).get(); // Wait for completion
                    
                    return result;
                    
                } finally {
                    // Always close the connection (only for SQL databases)
                    if (connection != null && !connection.isClosed()) {
                        connection.close();
                    }
                }
                
            } catch (Exception e) {
                logger.error("Schema analysis failed for connection {} (Request ID: {}): {}", 
                    command.getConnectionId(), requestId, e.getMessage(), e);
                
                return new DatabaseSchemaAgent.SchemaAnalysisResult(
                    command.getConnectionId(),
                    command.getUserId(),
                    command.getDatabaseType(),
                    null,
                    0,
                    false,
                    e.getMessage(),
                    0
                );
            }
        }).whenComplete((result, throwable) -> {
            try {
                activeAnalysisRequests.decrementAndGet();
                
                if (throwable != null) {
                    logger.error("Async schema analysis failed for connection {} (Request ID: {}): {}", 
                        command.getConnectionId(), requestId, throwable.getMessage());
                    
                    command.getReplyTo().tell(new SchemaAnalysisResponse(
                        command.getConnectionId(),
                        command.getUserId(),
                        false,
                        "Schema analysis failed",
                        0,
                        0,
                        0,
                        throwable.getMessage()
                    ));
                } else {
                    if (result.isSuccess()) {
                        logger.info("Schema analysis completed successfully for connection {} (Request ID: {}) - Tables: {}, Embeddings: {}, Time: {}ms", 
                            command.getConnectionId(), requestId, 
                            result.getSchemaInfo() != null ? result.getSchemaInfo().getTables().size() : 0,
                            result.getEmbeddingsGenerated(), result.getProcessingTimeMs());
                        
                        command.getReplyTo().tell(new SchemaAnalysisResponse(
                            result.getConnectionId(),
                            result.getUserId(),
                            true,
                            "Schema analysis completed successfully",
                            result.getSchemaInfo() != null ? result.getSchemaInfo().getTables().size() : 0,
                            result.getEmbeddingsGenerated(),
                            result.getProcessingTimeMs(),
                            null
                        ));
                    } else {
                        logger.error("Schema analysis failed for connection {} (Request ID: {}): {}", 
                            command.getConnectionId(), requestId, result.getError());
                        
                        command.getReplyTo().tell(new SchemaAnalysisResponse(
                            result.getConnectionId(),
                            result.getUserId(),
                            false,
                            "Schema analysis failed",
                            0,
                            0,
                            result.getProcessingTimeMs(),
                            result.getError()
                        ));
                    }
                }
            } catch (Exception e) {
                logger.error("Error in schema analysis completion handler (Request ID: {}): {}", requestId, e.getMessage(), e);
            }
        });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onSearchSimilarSchemas(SearchSimilarSchemas command) {
        logger.info("Searching for similar schemas with query: '{}' (limit: {}) for user: {}", 
            command.getQuery(), command.getLimit(), command.getUserId());
        
        // Perform similarity search asynchronously
        schemaAgent.findSimilarSchemas(command.getQuery(), command.getLimit(), command.getConnectionId())
            .whenComplete((results, throwable) -> {
                if (throwable != null) {
                    logger.error("Similar schema search failed for user {}: {}", 
                        command.getUserId(), throwable.getMessage(), throwable);
                    
                    command.getReplyTo().tell(new SimilarSchemasResponse(
                        java.util.Collections.emptyList(), false, throwable.getMessage()
                    ));
                } else {
                    logger.info("Found {} similar schemas for user {} with query: '{}'", 
                        results.size(), command.getUserId(), command.getQuery());
                    
                    command.getReplyTo().tell(new SimilarSchemasResponse(results, true, null));
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetAnalysisStats(GetAnalysisStats command) {
        logger.debug("Providing schema analysis statistics");
        
        try {
            DatabaseSchemaAgent.SchemaAgentStats agentStats = schemaAgent.getStats();
            
            command.getReplyTo().tell(new AnalysisStatsResponse(
                activeAnalysisRequests.get(),
                totalAnalysisRequests.get(),
                agentStats,
                true
            ));
        } catch (Exception e) {
            logger.error("Error getting analysis stats: {}", e.getMessage(), e);
            command.getReplyTo().tell(new AnalysisStatsResponse(0, 0, null, false));
        }
        
        return Behaviors.same();
    }
    
    /**
     * Create database connection from UserDatabaseConnection
     */
    private Connection createDatabaseConnection(UserDatabaseConnection dbConnection) throws Exception {
        String connectionUrl = buildConnectionUrl(dbConnection);
        
        logger.debug("Creating database connection to: {} (type: {})", 
            dbConnection.getHost(), dbConnection.getDatabaseType());
        
        return DriverManager.getConnection(
            connectionUrl, 
            dbConnection.getUsername(), 
            dbConnection.getPassword()
        );
    }
    
    /**
     * Build connection URL based on database type
     */
    private String buildConnectionUrl(UserDatabaseConnection dbConnection) {
        DatabaseType dbType = DatabaseType.fromString(dbConnection.getDatabaseType());
        
        switch (dbType) {
            case POSTGRESQL:
                return String.format("jdbc:postgresql://%s:%d/%s", 
                    dbConnection.getHost(), dbConnection.getPort(), dbConnection.getDatabaseName());
            
            case MYSQL:
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", 
                    dbConnection.getHost(), dbConnection.getPort(), dbConnection.getDatabaseName());
            
            case MONGODB:
                return String.format("mongodb://%s:%d/%s", 
                    dbConnection.getHost(), dbConnection.getPort(), dbConnection.getDatabaseName());
            
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }
}

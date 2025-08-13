package com.dbVybe.app.actor.llm;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.service.agent.QueryExecutionAgent;
import com.dbVybe.app.domain.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QueryExecutorActor - Handles safe database query execution
 * 
 * This actor:
 * - Receives queries from QueryGenerationActor
 * - Delegates to QueryExecutionAgent for safe execution
 * - Validates query safety (blocks dangerous operations)
 * - Supports MySQL, PostgreSQL, and MongoDB
 * - Returns formatted query results
 */
public class QueryExecutorActor extends AbstractBehavior<QueryExecutorActor.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutorActor.class);
    
    private final QueryExecutionAgent queryExecutionAgent;
    private final AtomicLong activeExecutions = new AtomicLong(0);
    private final AtomicLong totalExecutions = new AtomicLong(0);
    
    // Actor Commands
    public interface Command {}
    
    /**
     * Execute a database query
     */
    public static class ExecuteQuery implements Command {
        private final String query;
        private final String connectionId;
        private final String userId;
        private final int maxRows;
        private final ActorRef<QueryExecutionResponse> replyTo;
        
        public ExecuteQuery(String query, String connectionId, String userId, int maxRows, 
                          ActorRef<QueryExecutionResponse> replyTo) {
            this.query = query;
            this.connectionId = connectionId;
            this.userId = userId;
            this.maxRows = maxRows;
            this.replyTo = replyTo;
        }
        
        public String getQuery() { return query; }
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public int getMaxRows() { return maxRows; }
        public ActorRef<QueryExecutionResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Validate query safety without executing
     */
    public static class ValidateQuery implements Command {
        private final String query;
        private final ActorRef<QueryValidationResponse> replyTo;
        
        public ValidateQuery(String query, ActorRef<QueryValidationResponse> replyTo) {
            this.query = query;
            this.replyTo = replyTo;
        }
        
        public String getQuery() { return query; }
        public ActorRef<QueryValidationResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get supported database types
     */
    public static class GetSupportedDatabases implements Command {
        private final ActorRef<SupportedDatabasesResponse> replyTo;
        
        public GetSupportedDatabases(ActorRef<SupportedDatabasesResponse> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<SupportedDatabasesResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get execution statistics
     */
    public static class GetExecutionStats implements Command {
        private final ActorRef<ExecutionStatsResponse> replyTo;
        
        public GetExecutionStats(ActorRef<ExecutionStatsResponse> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<ExecutionStatsResponse> getReplyTo() { return replyTo; }
    }
    
    // Response Classes
    
    public static class QueryExecutionResponse {
        private final boolean success;
        private final String error;
        private final QueryExecutionAgent.QueryResultData data;
        private final int rowCount;
        private final long executionTimeMs;
        private final String status;
        private final String requestId;
        
        public QueryExecutionResponse(boolean success, String error, 
                                    QueryExecutionAgent.QueryResultData data, 
                                    int rowCount, long executionTimeMs, String status, String requestId) {
            this.success = success;
            this.error = error;
            this.data = data;
            this.rowCount = rowCount;
            this.executionTimeMs = executionTimeMs;
            this.status = status;
            this.requestId = requestId;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public QueryExecutionAgent.QueryResultData getData() { return data; }
        public int getRowCount() { return rowCount; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getStatus() { return status; }
        public String getRequestId() { return requestId; }
    }
    
    public static class QueryValidationResponse {
        private final boolean valid;
        private final String reason;
        private final String requestId;
        
        public QueryValidationResponse(boolean valid, String reason, String requestId) {
            this.valid = valid;
            this.reason = reason;
            this.requestId = requestId;
        }
        
        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public String getRequestId() { return requestId; }
    }
    
    public static class SupportedDatabasesResponse {
        private final List<DatabaseType> supportedTypes;
        private final boolean success;
        
        public SupportedDatabasesResponse(List<DatabaseType> supportedTypes, boolean success) {
            this.supportedTypes = supportedTypes;
            this.success = success;
        }
        
        public List<DatabaseType> getSupportedTypes() { return supportedTypes; }
        public boolean isSuccess() { return success; }
    }
    
    public static class ExecutionStatsResponse {
        private final long activeExecutions;
        private final long totalExecutions;
        private final QueryExecutionAgent.QueryExecutionStats agentStats;
        private final boolean success;
        
        public ExecutionStatsResponse(long activeExecutions, long totalExecutions, 
                                    QueryExecutionAgent.QueryExecutionStats agentStats, boolean success) {
            this.activeExecutions = activeExecutions;
            this.totalExecutions = totalExecutions;
            this.agentStats = agentStats;
            this.success = success;
        }
        
        public long getActiveExecutions() { return activeExecutions; }
        public long getTotalExecutions() { return totalExecutions; }
        public QueryExecutionAgent.QueryExecutionStats getAgentStats() { return agentStats; }
        public boolean isSuccess() { return success; }
    }
    
    public static Behavior<Command> create(QueryExecutionAgent queryExecutionAgent) {
        return Behaviors.setup(context -> new QueryExecutorActor(context, queryExecutionAgent));
    }
    
    private QueryExecutorActor(ActorContext<Command> context, QueryExecutionAgent queryExecutionAgent) {
        super(context);
        this.queryExecutionAgent = queryExecutionAgent;
        logger.info("QueryExecutorActor created and ready for database query execution");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ExecuteQuery.class, this::onExecuteQuery)
            .onMessage(ValidateQuery.class, this::onValidateQuery)
            .onMessage(GetSupportedDatabases.class, this::onGetSupportedDatabases)
            .onMessage(GetExecutionStats.class, this::onGetExecutionStats)
            .build();
    }
    
    private Behavior<Command> onExecuteQuery(ExecuteQuery command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeExecutions.incrementAndGet();
        totalExecutions.incrementAndGet();
        
        logger.info("Executing query for user: {} on connection: {} (max rows: {}) - Request ID: {}", 
            command.getUserId(), command.getConnectionId(), command.getMaxRows(), requestId);
        logger.debug("Query to execute (Request ID: {}): {}", requestId, command.getQuery());
        
        queryExecutionAgent.executeQuery(
                command.getQuery(), 
                command.getConnectionId(), 
                command.getUserId(), 
                command.getMaxRows())
            .whenComplete((result, throwable) -> {
                try {
                    activeExecutions.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Query execution failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new QueryExecutionResponse(
                            false, 
                            "Execution failed: " + throwable.getMessage(),
                            null, 
                            0, 
                            0,
                            "ACTOR_ERROR", 
                            requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Query executed successfully (Request ID: {}) - Rows: {}, Time: {}ms", 
                                requestId, result.getRowCount(), result.getExecutionTimeMs());
                        } else {
                            logger.warn("Query execution failed (Request ID: {}): {} - Status: {}", 
                                requestId, result.getError(), result.getStatus());
                        }
                        
                        command.getReplyTo().tell(new QueryExecutionResponse(
                            result.isSuccess(),
                            result.getError(),
                            result.getData(),
                            result.getRowCount(),
                            result.getExecutionTimeMs(),
                            result.getStatus(),
                            requestId));
                    }
                } catch (Exception e) {
                    logger.error("Error in query execution completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onValidateQuery(ValidateQuery command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        
        logger.debug("Validating query safety (Request ID: {}): {}", requestId, command.getQuery());
        
        try {
            // Use a dummy execution to validate safety
            queryExecutionAgent.executeQuery(command.getQuery(), "validation", "system", 0)
                .whenComplete((result, throwable) -> {
                    try {
                        if (throwable != null) {
                            command.getReplyTo().tell(new QueryValidationResponse(
                                false, 
                                "Validation failed: " + throwable.getMessage(), 
                                requestId));
                        } else {
                            boolean isValid = result.isSuccess() || 
                                            !"BLOCKED".equals(result.getStatus());
                            String reason = isValid ? null : result.getError();
                            
                            command.getReplyTo().tell(new QueryValidationResponse(isValid, reason, requestId));
                        }
                    } catch (Exception e) {
                        logger.error("Error in query validation completion handler (Request ID: {}): {}", requestId, e.getMessage());
                    }
                });
                
        } catch (Exception e) {
            logger.error("Error validating query (Request ID: {}): {}", requestId, e.getMessage());
            command.getReplyTo().tell(new QueryValidationResponse(
                false, 
                "Validation error: " + e.getMessage(), 
                requestId));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetSupportedDatabases(GetSupportedDatabases command) {
        logger.debug("Providing supported database types");
        
        try {
            List<DatabaseType> supportedTypes = queryExecutionAgent.getSupportedDatabaseTypes();
            command.getReplyTo().tell(new SupportedDatabasesResponse(supportedTypes, true));
        } catch (Exception e) {
            logger.error("Error getting supported databases: {}", e.getMessage(), e);
            command.getReplyTo().tell(new SupportedDatabasesResponse(
                java.util.Collections.emptyList(), false));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetExecutionStats(GetExecutionStats command) {
        logger.debug("Providing query execution statistics");
        
        try {
            QueryExecutionAgent.QueryExecutionStats agentStats = queryExecutionAgent.getStats();
            
            command.getReplyTo().tell(new ExecutionStatsResponse(
                activeExecutions.get(),
                totalExecutions.get(),
                agentStats,
                true
            ));
        } catch (Exception e) {
            logger.error("Error getting execution stats: {}", e.getMessage(), e);
            command.getReplyTo().tell(new ExecutionStatsResponse(0, 0, null, false));
        }
        
        return Behaviors.same();
    }
}

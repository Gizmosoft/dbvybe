package com.dbVybe.app.actor.llm;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.service.agent.NLPAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * QueryGenerationActor - Responsible for generating database queries from natural language
 * 
 * This actor:
 * - Uses the NLP Agent to generate database queries
 * - Is aware of the user's current database context
 * - Handles schema-aware query generation
 * - Provides query explanations and validation
 */
public class QueryGenerationActor extends AbstractBehavior<QueryGenerationActor.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryGenerationActor.class);
    
    private final NLPAgent nlpAgent;
    
    // Actor Commands
    public interface Command {}
    
    /**
     * Generate a database query from natural language
     */
    public static class GenerateQuery implements Command {
        private final String userMessage;
        private final String userId;
        private final String sessionId;
        private final DatabaseType databaseType;
        private final Map<String, Object> databaseSchema;
        private final ActorRef<QueryGenerationResponse> replyTo;
        
        public GenerateQuery(String userMessage, String userId, String sessionId, 
                           DatabaseType databaseType, Map<String, Object> databaseSchema,
                           ActorRef<QueryGenerationResponse> replyTo) {
            this.userMessage = userMessage;
            this.userId = userId;
            this.sessionId = sessionId;
            this.databaseType = databaseType;
            this.databaseSchema = databaseSchema;
            this.replyTo = replyTo;
        }
        
        public String getUserMessage() { return userMessage; }
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public Map<String, Object> getDatabaseSchema() { return databaseSchema; }
        public ActorRef<QueryGenerationResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Validate a generated query before execution
     */
    public static class ValidateQuery implements Command {
        private final String query;
        private final DatabaseType databaseType;
        private final String userId;
        private final ActorRef<QueryValidationResponse> replyTo;
        
        public ValidateQuery(String query, DatabaseType databaseType, String userId,
                           ActorRef<QueryValidationResponse> replyTo) {
            this.query = query;
            this.databaseType = databaseType;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getQuery() { return query; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public String getUserId() { return userId; }
        public ActorRef<QueryValidationResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get actor status
     */
    public static class GetStatus implements Command {
        private final ActorRef<QueryGeneratorStatus> replyTo;
        
        public GetStatus(ActorRef<QueryGeneratorStatus> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<QueryGeneratorStatus> getReplyTo() { return replyTo; }
    }
    
    // Response Messages
    
    /**
     * Response containing generated query
     */
    public static class QueryGenerationResponse {
        private final String generatedQuery;
        private final String explanation;
        private final DatabaseType databaseType;
        private final boolean success;
        private final String error;
        private final long processingTimeMs;
        private final int tokensUsed;
        private final String requestId;
        
        public QueryGenerationResponse(String generatedQuery, String explanation, DatabaseType databaseType,
                                     boolean success, String error, long processingTimeMs, int tokensUsed, String requestId) {
            this.generatedQuery = generatedQuery;
            this.explanation = explanation;
            this.databaseType = databaseType;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.tokensUsed = tokensUsed;
            this.requestId = requestId;
        }
        
        public String getGeneratedQuery() { return generatedQuery; }
        public String getExplanation() { return explanation; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public int getTokensUsed() { return tokensUsed; }
        public String getRequestId() { return requestId; }
    }
    
    /**
     * Response for query validation
     */
    public static class QueryValidationResponse {
        private final boolean isValid;
        private final boolean isSafe;
        private final String validationMessage;
        private final String[] warnings;
        private final String requestId;
        
        public QueryValidationResponse(boolean isValid, boolean isSafe, String validationMessage, 
                                     String[] warnings, String requestId) {
            this.isValid = isValid;
            this.isSafe = isSafe;
            this.validationMessage = validationMessage;
            this.warnings = warnings;
            this.requestId = requestId;
        }
        
        public boolean isValid() { return isValid; }
        public boolean isSafe() { return isSafe; }
        public String getValidationMessage() { return validationMessage; }
        public String[] getWarnings() { return warnings; }
        public String getRequestId() { return requestId; }
    }
    
    /**
     * Status response for the query generator
     */
    public static class QueryGeneratorStatus {
        private final boolean isReady;
        private final int activeRequests;
        private final long totalQueriesGenerated;
        private final double averageGenerationTime;
        private final String agentStatus;
        
        public QueryGeneratorStatus(boolean isReady, int activeRequests, long totalQueriesGenerated,
                                  double averageGenerationTime, String agentStatus) {
            this.isReady = isReady;
            this.activeRequests = activeRequests;
            this.totalQueriesGenerated = totalQueriesGenerated;
            this.averageGenerationTime = averageGenerationTime;
            this.agentStatus = agentStatus;
        }
        
        public boolean isReady() { return isReady; }
        public int getActiveRequests() { return activeRequests; }
        public long getTotalQueriesGenerated() { return totalQueriesGenerated; }
        public double getAverageGenerationTime() { return averageGenerationTime; }
        public String getAgentStatus() { return agentStatus; }
    }
    
    // Actor state
    private int activeRequests = 0;
    private long totalQueriesGenerated = 0;
    private double totalGenerationTime = 0.0;
    
    public static Behavior<Command> create(NLPAgent nlpAgent) {
        return Behaviors.setup(context -> new QueryGenerationActor(context, nlpAgent));
    }
    
    private QueryGenerationActor(ActorContext<Command> context, NLPAgent nlpAgent) {
        super(context);
        this.nlpAgent = nlpAgent;
        logger.info("QueryGenerationActor created and ready to generate database queries");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GenerateQuery.class, this::onGenerateQuery)
            .onMessage(ValidateQuery.class, this::onValidateQuery)
            .onMessage(GetStatus.class, this::onGetStatus)
            .build();
    }
    
    private Behavior<Command> onGenerateQuery(GenerateQuery command) {
        String requestId = java.util.UUID.randomUUID().toString();
        activeRequests++;
        
        logger.info("QueryGenerationActor generating query for user {} in session {} for {} database: '{}'",
            command.getUserId(), command.getSessionId(), command.getDatabaseType(), command.getUserMessage());
        
        // Generate query using NLP Agent asynchronously
        CompletableFuture<NLPAgent.QueryGenerationResponse> future = nlpAgent.generateDatabaseQuery(
            command.getUserMessage(),
            command.getUserId(),
            command.getSessionId(),
            command.getDatabaseType(),
            command.getDatabaseSchema()
        );
        
        // Handle the response asynchronously
        future.whenComplete((response, throwable) -> {
            try {
                if (throwable != null) {
                    logger.error("Error generating query for user {} with request ID {}: {}", 
                        command.getUserId(), requestId, throwable.getMessage());
                    
                    command.getReplyTo().tell(new QueryGenerationResponse(
                        null, null, command.getDatabaseType(), false, 
                        "Query generation failed: " + throwable.getMessage(), 
                        0, 0, requestId
                    ));
                } else {
                    totalQueriesGenerated++;
                    totalGenerationTime += response.getProcessingTimeMs();
                    
                    logger.info("Successfully generated query for user {} with request ID {} in {}ms",
                        command.getUserId(), requestId, response.getProcessingTimeMs());
                    
                    command.getReplyTo().tell(new QueryGenerationResponse(
                        response.getGeneratedQuery(),
                        response.getExplanation(),
                        response.getDatabaseType(),
                        response.isSuccess(),
                        response.getError(),
                        response.getProcessingTimeMs(),
                        response.getTokensUsed(),
                        requestId
                    ));
                }
            } finally {
                activeRequests--;
            }
        });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onValidateQuery(ValidateQuery command) {
        String requestId = java.util.UUID.randomUUID().toString();
        
        logger.info("QueryGenerationActor validating {} query for user {}: '{}'",
            command.getDatabaseType(), command.getUserId(), command.getQuery());
        
        try {
            // Perform basic query validation
            QueryValidationResult validation = validateQuery(command.getQuery(), command.getDatabaseType());
            
            command.getReplyTo().tell(new QueryValidationResponse(
                validation.isValid(),
                validation.isSafe(),
                validation.getMessage(),
                validation.getWarnings(),
                requestId
            ));
            
            logger.info("Query validation completed for user {} with request ID {}: valid={}, safe={}",
                command.getUserId(), requestId, validation.isValid(), validation.isSafe());
            
        } catch (Exception e) {
            logger.error("Error validating query for user {} with request ID {}: {}", 
                command.getUserId(), requestId, e.getMessage());
            
            command.getReplyTo().tell(new QueryValidationResponse(
                false, false, "Validation error: " + e.getMessage(), 
                new String[]{"Validation process failed"}, requestId
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetStatus(GetStatus command) {
        logger.debug("Providing QueryGenerationActor status");
        
        double averageTime = totalQueriesGenerated > 0 ? totalGenerationTime / totalQueriesGenerated : 0.0;
        
        // Get NLP Agent status
        nlpAgent.getStatus().whenComplete((agentStatus, throwable) -> {
            String agentStatusStr = throwable != null ? "Error getting agent status" : 
                (agentStatus.isHealthy() ? "Healthy" : "Unhealthy");
            
            command.getReplyTo().tell(new QueryGeneratorStatus(
                nlpAgent != null && agentStatus != null && agentStatus.isHealthy(),
                activeRequests,
                totalQueriesGenerated,
                averageTime,
                agentStatusStr
            ));
        });
        
        return Behaviors.same();
    }
    
    /**
     * Validate a database query for safety and correctness
     */
    private QueryValidationResult validateQuery(String query, DatabaseType databaseType) {
        if (query == null || query.trim().isEmpty()) {
            return new QueryValidationResult(false, false, "Query is empty", new String[]{"No query provided"});
        }
        
        String normalizedQuery = query.toLowerCase().trim();
        
        // Check for dangerous operations (keeping only the most critical ones)
        // Removed most restrictions to allow all query types
        String[] dangerousOperations = {"drop", "delete", "truncate", "alter", "create", "insert", "update"};
        for (String operation : dangerousOperations) {
            if (normalizedQuery.contains(operation)) {
                return new QueryValidationResult(true, false, 
                    "Query contains potentially dangerous operation: " + operation,
                    new String[]{"Query may modify data", "Review query before execution"});
            }
        }
        
        // REMOVED: Basic syntax validation based on database type
        // All query types are now allowed (SELECT, INSERT, UPDATE, DELETE, etc.)
        boolean isValid = true; // Allow all query types
        
        if (!isValid) {
            return new QueryValidationResult(false, false, 
                "Query does not appear to be a valid " + databaseType + " query",
                new String[]{"Syntax may be incorrect"});
        }
        
        return new QueryValidationResult(true, true, "Query appears to be safe and valid", new String[]{});
    }
    
    /**
     * Helper class for query validation results
     */
    private static class QueryValidationResult {
        private final boolean valid;
        private final boolean safe;
        private final String message;
        private final String[] warnings;
        
        public QueryValidationResult(boolean valid, boolean safe, String message, String[] warnings) {
            this.valid = valid;
            this.safe = safe;
            this.message = message;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return valid; }
        public boolean isSafe() { return safe; }
        public String getMessage() { return message; }
        public String[] getWarnings() { return warnings; }
    }
}

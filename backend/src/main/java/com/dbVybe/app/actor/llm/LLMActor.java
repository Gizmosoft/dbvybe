package com.dbVybe.app.actor.llm;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.ActorRef;
import com.dbVybe.app.service.llm.LLMService;
import com.dbVybe.app.service.agent.NLPAgent;
import com.dbVybe.app.domain.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * LLMActor - Intelligent request router and mediator
 * 
 * This actor:
 * - Routes requests intelligently between general chat and query generation
 * - Uses NLP Agent for intelligent processing
 * - Delegates to QueryGenerationActor for database query requests
 * - Handles general chat messages directly through NLP Agent
 */
public class LLMActor extends AbstractBehavior<LLMActor.Command> {
    
    private final Logger logger = LoggerFactory.getLogger(LLMActor.class);
    private final LLMService llmService; // Kept for backward compatibility
    private final NLPAgent nlpAgent;
    private final ActorRef<QueryGenerationActor.Command> queryGenerationActor;
    
    public interface Command {}
    
    /**
     * Process a message with intelligent routing
     */
    public static class ProcessMessage implements Command {
        private final String userMessage;
        private final String userId;
        private final String sessionId;
        private final DatabaseType databaseType; // User's current database context
        private final Map<String, Object> databaseSchema; // Schema information
        private final ActorRef<ProcessMessageResponse> replyTo;
        
        public ProcessMessage(String userMessage, String userId, String sessionId, 
                            DatabaseType databaseType, Map<String, Object> databaseSchema,
                            ActorRef<ProcessMessageResponse> replyTo) {
            this.userMessage = userMessage;
            this.userId = userId;
            this.sessionId = sessionId;
            this.databaseType = databaseType;
            this.databaseSchema = databaseSchema;
            this.replyTo = replyTo;
        }
        
        // Backward compatibility constructor
        public ProcessMessage(String userMessage, String userId, String sessionId, ActorRef<ProcessMessageResponse> replyTo) {
            this(userMessage, userId, sessionId, null, null, replyTo);
        }
        
        public String getUserMessage() { return userMessage; }
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public Map<String, Object> getDatabaseSchema() { return databaseSchema; }
        public ActorRef<ProcessMessageResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Response from LLM processing with intelligent routing information
     */
    public static class ProcessMessageResponse {
        private final String content;
        private final boolean success;
        private final String error;
        private final long processingTimeMs;
        private final int tokenCount;
        private final String modelUsed;
        private final boolean wasQueryGenerated; // Indicates if this was a query generation request
        private final String generatedQuery; // The actual query if generated
        private final String queryExplanation; // Explanation of the query
        
        public ProcessMessageResponse(String content, boolean success, String error, 
                                    long processingTimeMs, int tokenCount, String modelUsed,
                                    boolean wasQueryGenerated, String generatedQuery, String queryExplanation) {
            this.content = content;
            this.success = success;
            this.error = error;
            this.processingTimeMs = processingTimeMs;
            this.tokenCount = tokenCount;
            this.modelUsed = modelUsed;
            this.wasQueryGenerated = wasQueryGenerated;
            this.generatedQuery = generatedQuery;
            this.queryExplanation = queryExplanation;
        }
        
        // General chat success constructor
        public ProcessMessageResponse(String content, long processingTimeMs, int tokenCount, String modelUsed) {
            this(content, true, null, processingTimeMs, tokenCount, modelUsed, false, null, null);
        }
        
        // Query generation success constructor
        public ProcessMessageResponse(String content, long processingTimeMs, int tokenCount, String modelUsed,
                                    String generatedQuery, String queryExplanation) {
            this(content, true, null, processingTimeMs, tokenCount, modelUsed, true, generatedQuery, queryExplanation);
        }
        
        // Error constructor
        public ProcessMessageResponse(String error, long processingTimeMs) {
            this(null, false, error, processingTimeMs, 0, null, false, null, null);
        }
        
        // Getters
        public String getContent() { return content; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public int getTokenCount() { return tokenCount; }
        public String getModelUsed() { return modelUsed; }
        public boolean wasQueryGenerated() { return wasQueryGenerated; }
        public String getGeneratedQuery() { return generatedQuery; }
        public String getQueryExplanation() { return queryExplanation; }
    }
    
    /**
     * Get LLM service health status
     */
    public static class GetHealth implements Command {
        private final ActorRef<HealthResponse> replyTo;
        
        public GetHealth(ActorRef<HealthResponse> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<HealthResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Health response from LLM service
     */
    public static class HealthResponse {
        private final boolean healthy;
        private final String serviceName;
        private final String modelName;
        private final LLMService.LLMUsageStats usageStats;
        
        public HealthResponse(boolean healthy, String serviceName, String modelName, LLMService.LLMUsageStats usageStats) {
            this.healthy = healthy;
            this.serviceName = serviceName;
            this.modelName = modelName;
            this.usageStats = usageStats;
        }
        
        // Getters
        public boolean isHealthy() { return healthy; }
        public String getServiceName() { return serviceName; }
        public String getModelName() { return modelName; }
        public LLMService.LLMUsageStats getUsageStats() { return usageStats; }
    }
    
    public static Behavior<Command> create(LLMService llmService) {
        return Behaviors.setup(context -> new LLMActor(context, llmService, null, null));
    }
    
    public static Behavior<Command> create(LLMService llmService, NLPAgent nlpAgent, 
                                         ActorRef<QueryGenerationActor.Command> queryGenerationActor) {
        return Behaviors.setup(context -> new LLMActor(context, llmService, nlpAgent, queryGenerationActor));
    }
    
    private LLMActor(ActorContext<Command> context, LLMService llmService, NLPAgent nlpAgent,
                    ActorRef<QueryGenerationActor.Command> queryGenerationActor) {
        super(context);
        this.llmService = llmService;
        this.nlpAgent = nlpAgent;
        this.queryGenerationActor = queryGenerationActor;
        
        if (nlpAgent != null && queryGenerationActor != null) {
            logger.info("LLMActor created with intelligent routing - NLP Agent and QueryGenerationActor integrated");
        } else {
            logger.info("LLMActor created with basic LLM service: {} using model: {}", 
                llmService.getServiceName(), llmService.getModelName());
        }
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ProcessMessage.class, this::onProcessMessage)
            .onMessage(GetHealth.class, this::onGetHealth)
            .build();
    }
    
    private Behavior<Command> onProcessMessage(ProcessMessage command) {
        logger.info("LLMActor intelligently processing message from user {} in session {}: '{}'", 
            command.getUserId(), command.getSessionId(), command.getUserMessage());
        
        // If we don't have NLP Agent, fall back to basic LLM processing
        if (nlpAgent == null) {
            return processWithBasicLLM(command);
        }
        
        // Step 1: Determine if this requires query generation
        CompletableFuture<Boolean> classificationFuture = nlpAgent.requiresQueryGeneration(command.getUserMessage());
        
        classificationFuture.whenComplete((requiresQuery, throwable) -> {
            if (throwable != null) {
                logger.error("Error classifying message for user {}: {}", command.getUserId(), throwable.getMessage());
                command.getReplyTo().tell(new ProcessMessageResponse(
                    "Classification error: " + throwable.getMessage(), 0
                ));
                return;
            }
            
            if (requiresQuery && command.getDatabaseType() != null && queryGenerationActor != null) {
                // Route to query generation
                logger.info("Routing message from user {} to QueryGenerationActor for database query generation", 
                    command.getUserId());
                routeToQueryGeneration(command);
            } else {
                // Handle as general chat
                logger.info("Processing message from user {} as general chat using NLP Agent", 
                    command.getUserId());
                processAsGeneralChat(command);
            }
        });
        
        return Behaviors.same();
    }
    
    /**
     * Process message with basic LLM service (backward compatibility)
     */
    private Behavior<Command> processWithBasicLLM(ProcessMessage command) {
        CompletableFuture<LLMService.LLMResponse> future = llmService.processMessage(
            command.getUserMessage(), 
            command.getUserId(), 
            command.getSessionId()
        );
        
        future.whenComplete((llmResponse, throwable) -> {
            if (throwable != null) {
                logger.error("Error processing message for user {}: {}", command.getUserId(), throwable.getMessage());
                command.getReplyTo().tell(new ProcessMessageResponse(
                    "LLM processing error: " + throwable.getMessage(), 0
                ));
            } else {
                logger.info("Successfully processed message for user {} in {}ms using {} tokens", 
                    command.getUserId(), llmResponse.getProcessingTimeMs(), llmResponse.getTokenCount());
                command.getReplyTo().tell(new ProcessMessageResponse(
                    llmResponse.getContent(),
                    llmResponse.getProcessingTimeMs(),
                    llmResponse.getTokenCount(),
                    llmResponse.getModelUsed()
                ));
            }
        });
        
        return Behaviors.same();
    }
    
    /**
     * Route message to QueryGenerationActor for database query generation
     */
    private void routeToQueryGeneration(ProcessMessage command) {
        // Create a response handler for the query generation result
        ActorRef<QueryGenerationActor.QueryGenerationResponse> responseHandler = 
            getContext().spawn(Behaviors.receiveMessage(queryResponse -> {
                if (queryResponse.isSuccess()) {
                    String responseContent = String.format(
                        "I've generated a %s query for you:\n\n**Query:**\n```sql\n%s\n```\n\n**Explanation:**\n%s",
                        queryResponse.getDatabaseType(),
                        queryResponse.getGeneratedQuery(),
                        queryResponse.getExplanation()
                    );
                    
                    command.getReplyTo().tell(new ProcessMessageResponse(
                        responseContent,
                        queryResponse.getProcessingTimeMs(),
                        queryResponse.getTokensUsed(),
                        "NLP Agent + " + queryResponse.getDatabaseType(),
                        queryResponse.getGeneratedQuery(),
                        queryResponse.getExplanation()
                    ));
                } else {
                    command.getReplyTo().tell(new ProcessMessageResponse(
                        "Query generation failed: " + queryResponse.getError(),
                        queryResponse.getProcessingTimeMs()
                    ));
                }
                return Behaviors.stopped();
            }), "query-response-handler-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        
        // Send query generation request
        queryGenerationActor.tell(new QueryGenerationActor.GenerateQuery(
            command.getUserMessage(),
            command.getUserId(),
            command.getSessionId(),
            command.getDatabaseType(),
            command.getDatabaseSchema(),
            responseHandler
        ));
    }
    
    /**
     * Process message as general chat using NLP Agent
     */
    private void processAsGeneralChat(ProcessMessage command) {
        CompletableFuture<NLPAgent.AgentResponse> future = nlpAgent.processGeneralMessage(
            command.getUserMessage(),
            command.getUserId(),
            command.getSessionId()
        );
        
        future.whenComplete((agentResponse, throwable) -> {
            if (throwable != null) {
                logger.error("Error processing general message for user {}: {}", command.getUserId(), throwable.getMessage());
                command.getReplyTo().tell(new ProcessMessageResponse(
                    "NLP Agent error: " + throwable.getMessage(), 0
                ));
            } else {
                logger.info("Successfully processed general message for user {} in {}ms using {} tokens", 
                    command.getUserId(), agentResponse.getProcessingTimeMs(), agentResponse.getTokensUsed());
                command.getReplyTo().tell(new ProcessMessageResponse(
                    agentResponse.getContent(),
                    agentResponse.getProcessingTimeMs(),
                    agentResponse.getTokensUsed(),
                    "NLP Agent"
                ));
            }
        });
    }
    
    private Behavior<Command> onGetHealth(GetHealth command) {
        logger.debug("Checking LLM service health");
        
        // Get health status asynchronously
        CompletableFuture<Boolean> healthFuture = llmService.isHealthy();
        
        healthFuture.whenComplete((healthy, throwable) -> {
            boolean isHealthy = throwable == null && healthy;
            command.getReplyTo().tell(new HealthResponse(
                isHealthy,
                llmService.getServiceName(),
                llmService.getModelName(),
                llmService.getUsageStats()
            ));
        });
        
        return Behaviors.same();
    }
}

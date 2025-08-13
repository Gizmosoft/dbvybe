package com.dbVybe.app.cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.actor.llm.LLMActor;
import com.dbVybe.app.actor.llm.QueryGenerationActor;
import com.dbVybe.app.service.llm.LLMService;
import com.dbVybe.app.service.agent.NLPAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/**
 * Node 2: LLM Processing Node with LLMProcessingSystem as the ActorSystem
 * 
 * This node handles:
 * - Natural language query processing
 * - Query translation to SQL
 * - LLM model management
 * - Response generation
 */
public class LLMProcessingSystem extends ClusterNode {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMProcessingSystem.class);
    private ActorRef<LLMOrchestrator.Command> llmOrchestrator;
    private ActorRef<LLMActor.Command> llmActor;
    private ActorRef<QueryGenerationActor.Command> queryGenerationActor;
    private LLMService llmService;
    private NLPAgent nlpAgent;
    
    public LLMProcessingSystem() {
        super("LLMProcessingSystem", "application-llm-node.conf");
        // Default constructor for backwards compatibility
        this.llmService = null; // Will be set via setter
    }
    
    public LLMProcessingSystem(LLMService llmService) {
        super("LLMProcessingSystem", "application-llm-node.conf");
        this.llmService = llmService;
        this.nlpAgent = null; // Will be injected separately
    }
    
    public LLMProcessingSystem(LLMService llmService, NLPAgent nlpAgent) {
        super("LLMProcessingSystem", "application-llm-node.conf");
        this.llmService = llmService;
        this.nlpAgent = nlpAgent;
        
        // Initialize the NLP Agent if provided
        if (nlpAgent instanceof com.dbVybe.app.service.agent.LangChainNLPAgent) {
            ((com.dbVybe.app.service.agent.LangChainNLPAgent) nlpAgent).initialize();
        }
    }
    
    @Override
    protected Behavior<Void> createRootBehavior() {
        return Behaviors.setup(context -> {
            logger.info("Creating LLMProcessingSystem root behavior with AI Agent integration");
            
            // Ensure LLM service is properly configured
            if (llmService == null) {
                logger.error("LLMService is null! Cannot create LLMActor");
                throw new IllegalStateException("LLMService must be provided to LLMProcessingSystem");
            }
            
            if (nlpAgent != null) {
                // Create QueryGenerationActor with NLP Agent
                queryGenerationActor = context.spawn(QueryGenerationActor.create(nlpAgent), "query-generation-actor");
                
                // Create the enhanced LLMActor with intelligent routing
                llmActor = context.spawn(LLMActor.create(llmService, nlpAgent, queryGenerationActor), "llm-actor");
                
                logger.info("Created enhanced LLMActor with NLP Agent and QueryGenerationActor integration");
            } else {
                // Fallback to basic LLMActor for backward compatibility
                llmActor = context.spawn(LLMActor.create(llmService), "llm-actor");
                
                logger.info("Created basic LLMActor (no NLP Agent available)");
            }
            
            // Create the LLM Orchestrator actor and pass LLMActor reference
            llmOrchestrator = context.spawn(LLMOrchestrator.create(llmActor), "llm-orchestrator");
            
            // Create the main supervisor actor for LLM processing
            context.spawn(LLMProcessingSupervisor.create(), "llm-processing-supervisor");
            
            logger.info("LLMProcessingSystem actor hierarchy created with {} integration", 
                nlpAgent != null ? "AI Agent" : "basic LLM");
            
            return Behaviors.empty();
        });
    }
    
    /**
     * Get LLMOrchestrator reference
     */
    public ActorRef<LLMOrchestrator.Command> getLLMOrchestrator() {
        return llmOrchestrator;
    }
    
    /**
     * Get LLMActor reference
     */
    public ActorRef<LLMActor.Command> getLLMActor() {
        return llmActor;
    }
    
    /**
     * Get QueryGenerationActor reference
     */
    public ActorRef<QueryGenerationActor.Command> getQueryGenerationActor() {
        return queryGenerationActor;
    }
    
    /**
     * LLMOrchestrator - Main actor for coordinating LLM requests
     * Handles chat interface requests and coordinates LLM processing
     */
    public static class LLMOrchestrator extends AbstractBehavior<LLMOrchestrator.Command> {
        
        private final Logger logger = LoggerFactory.getLogger(LLMOrchestrator.class);
        private final ActorRef<LLMActor.Command> llmActor;
        
        public interface Command {}
        
        /**
         * Process a chat message from user
         */
        public static class ProcessChatMessage implements Command {
            private final String userId;
            private final String message;
            private final String sessionId;
            private final ActorRef<ChatResponse> replyTo;
            
            public ProcessChatMessage(String userId, String message, String sessionId, ActorRef<ChatResponse> replyTo) {
                this.userId = userId;
                this.message = message;
                this.sessionId = sessionId;
                this.replyTo = replyTo;
            }
            
            public String getUserId() { return userId; }
            public String getMessage() { return message; }
            public String getSessionId() { return sessionId; }
            public ActorRef<ChatResponse> getReplyTo() { return replyTo; }
        }
        
        /**
         * Response wrapper for chat messages
         */
        public static class ChatResponse {
            private final String requestId;
            private final String response;
            private final boolean success;
            private final String error;
            
            public ChatResponse(String requestId, String response, boolean success, String error) {
                this.requestId = requestId;
                this.response = response;
                this.success = success;
                this.error = error;
            }
            
            public String getRequestId() { return requestId; }
            public String getResponse() { return response; }
            public boolean isSuccess() { return success; }
            public String getError() { return error; }
        }
        
        /**
         * Get status of LLM processing
         */
        public static class GetStatus implements Command {
            private final ActorRef<StatusResponse> replyTo;
            
            public GetStatus(ActorRef<StatusResponse> replyTo) {
                this.replyTo = replyTo;
            }
            
            public ActorRef<StatusResponse> getReplyTo() { return replyTo; }
        }
        
        /**
         * Status response for LLM orchestrator
         */
        public static class StatusResponse {
            private final boolean isReady;
            private final int activeRequests;
            private final String status;
            
            public StatusResponse(boolean isReady, int activeRequests, String status) {
                this.isReady = isReady;
                this.activeRequests = activeRequests;
                this.status = status;
            }
            
            public boolean isReady() { return isReady; }
            public int getActiveRequests() { return activeRequests; }
            public String getStatus() { return status; }
        }
        
        private int activeRequests = 0;
        
        public static Behavior<Command> create(ActorRef<LLMActor.Command> llmActor) {
            return Behaviors.setup(context -> new LLMOrchestrator(context, llmActor));
        }
        
        private LLMOrchestrator(ActorContext<Command> context, ActorRef<LLMActor.Command> llmActor) {
            super(context);
            this.llmActor = llmActor;
            logger.info("LLMOrchestrator created and ready to process chat messages with LLMActor integration");
        }
        
        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                .onMessage(ProcessChatMessage.class, this::onProcessChatMessage)
                .onMessage(GetStatus.class, this::onGetStatus)
                .build();
        }
        
        private Behavior<Command> onProcessChatMessage(ProcessChatMessage command) {
            String requestId = UUID.randomUUID().toString();
            activeRequests++;
            
            logger.info("LLMOrchestrator delegating chat message from user {} in session {}: '{}'", 
                command.getUserId(), command.getSessionId(), command.getMessage());
            
            // Create a response handler actor for this specific request
            ActorRef<LLMActor.ProcessMessageResponse> responseHandler = 
                getContext().spawn(Behaviors.receiveMessage(response -> {
                    try {
                        if (response.isSuccess()) {
                            command.getReplyTo().tell(new ChatResponse(requestId, response.getContent(), true, null));
                            logger.info("Successfully processed chat message for user {} with request ID {} in {}ms", 
                                command.getUserId(), requestId, response.getProcessingTimeMs());
                        } else {
                            command.getReplyTo().tell(new ChatResponse(requestId, null, false, response.getError()));
                            logger.error("Error processing chat message for user {} with request ID {}: {}", 
                                command.getUserId(), requestId, response.getError());
                        }
                    } finally {
                        activeRequests--;
                    }
                    return Behaviors.stopped();
                }), "response-handler-" + requestId);
            
            // Delegate to LLMActor
            llmActor.tell(new LLMActor.ProcessMessage(
                command.getMessage(),
                command.getUserId(),
                command.getSessionId(),
                responseHandler
            ));
            
            return Behaviors.same();
        }
        
        private Behavior<Command> onGetStatus(GetStatus command) {
            logger.debug("Providing LLM orchestrator status");
            command.getReplyTo().tell(new StatusResponse(true, activeRequests, "READY"));
            return Behaviors.same();
        }
    }
    
    /**
     * Main supervisor actor for LLM processing services
     */
    public static class LLMProcessingSupervisor extends AbstractBehavior<LLMProcessingSupervisor.Command> {
        
        public interface Command {}
        
        public static class ProcessQuery implements Command {
            private final String naturalLanguageQuery;
            private final String requestId;
            
            public ProcessQuery(String naturalLanguageQuery, String requestId) {
                this.naturalLanguageQuery = naturalLanguageQuery;
                this.requestId = requestId;
            }
            
            public String getNaturalLanguageQuery() {
                return naturalLanguageQuery;
            }
            
            public String getRequestId() {
                return requestId;
            }
        }
        
        public static class StopProcessing implements Command {}
        
        public static Behavior<Command> create() {
            return Behaviors.setup(LLMProcessingSupervisor::new);
        }
        
        private final Logger logger = LoggerFactory.getLogger(LLMProcessingSupervisor.class);
        
        private LLMProcessingSupervisor(ActorContext<Command> context) {
            super(context);
            logger.info("LLMProcessingSupervisor created");
        }
        
        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                .onMessage(ProcessQuery.class, this::onProcessQuery)
                .onMessage(StopProcessing.class, this::onStopProcessing)
                .build();
        }
        
        private Behavior<Command> onProcessQuery(ProcessQuery command) {
            logger.info("Processing LLM query: {} with request ID: {}", 
                command.getNaturalLanguageQuery(), command.getRequestId());
            // TODO: Implement LLM processing logic
            return Behaviors.same();
        }
        
        private Behavior<Command> onStopProcessing(StopProcessing command) {
            logger.info("Stopping LLM processing");
            // TODO: Implement cleanup logic
            return Behaviors.same();
        }
    }
} 
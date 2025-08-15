package com.dbVybe.app.cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorRef;
import com.dbVybe.app.actor.llm.LLMActor;
import com.dbVybe.app.actor.llm.QueryGenerationActor;
import com.dbVybe.app.actor.llm.QueryExecutorActor;
import com.dbVybe.app.service.llm.LLMService;
import com.dbVybe.app.service.agent.NLPAgent;
import com.dbVybe.app.service.agent.QueryExecutionAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private ActorRef<com.dbVybe.app.actor.llm.LLMOrchestrator.Command> llmOrchestrator;
    private ActorRef<LLMActor.Command> llmActor;
    private ActorRef<QueryGenerationActor.Command> queryGenerationActor;
    private ActorRef<QueryExecutorActor.Command> queryExecutorActor;
    private LLMService llmService;
    private NLPAgent nlpAgent;
    private QueryExecutionAgent queryExecutionAgent;
    
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
        this.queryExecutionAgent = null; // Will be injected separately
        
        // Initialize the NLP Agent if provided
        if (nlpAgent instanceof com.dbVybe.app.service.agent.LangChainNLPAgent) {
            ((com.dbVybe.app.service.agent.LangChainNLPAgent) nlpAgent).initialize();
        }
    }
    
    public LLMProcessingSystem(LLMService llmService, NLPAgent nlpAgent, QueryExecutionAgent queryExecutionAgent) {
        super("LLMProcessingSystem", "application-llm-node.conf");
        this.llmService = llmService;
        this.nlpAgent = nlpAgent;
        this.queryExecutionAgent = queryExecutionAgent;
        
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
                
                // Create QueryExecutorActor with QueryExecutionAgent if available
                if (queryExecutionAgent != null) {
                    queryExecutorActor = context.spawn(QueryExecutorActor.create(queryExecutionAgent), "query-executor-actor");
                    logger.info("Created QueryExecutorActor with Query Execution Agent integration");
                } else {
                    logger.warn("QueryExecutionAgent not provided. Query execution will not be available.");
                }
                
                // Create the enhanced LLMActor with intelligent routing
                llmActor = context.spawn(LLMActor.create(llmService, nlpAgent, queryGenerationActor), "llm-actor");
                
                logger.info("Created enhanced LLMActor with NLP Agent and QueryGenerationActor integration");
            } else {
                // Fallback to basic LLMActor for backward compatibility
                llmActor = context.spawn(LLMActor.create(llmService), "llm-actor");
                
                logger.info("Created basic LLMActor (no NLP Agent available)");
            }
            
            // Create the LLM Orchestrator actor with AI Agent integration
            llmOrchestrator = context.spawn(com.dbVybe.app.actor.llm.LLMOrchestrator.create(llmService, nlpAgent, queryExecutionAgent), "llm-orchestrator");
            
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
    public ActorRef<com.dbVybe.app.actor.llm.LLMOrchestrator.Command> getLLMOrchestrator() {
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
     * Get QueryExecutorActor reference
     */
    public ActorRef<QueryExecutorActor.Command> getQueryExecutorActor() {
        return queryExecutorActor;
    }
    
    /**
     * Main supervisor actor for LLM processing services
     */
    public static class LLMProcessingSupervisor extends akka.actor.typed.javadsl.AbstractBehavior<LLMProcessingSupervisor.Command> {
        
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
        
        private LLMProcessingSupervisor(akka.actor.typed.javadsl.ActorContext<Command> context) {
            super(context);
            logger.info("LLMProcessingSupervisor created");
        }
        
        @Override
        public akka.actor.typed.javadsl.Receive<Command> createReceive() {
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
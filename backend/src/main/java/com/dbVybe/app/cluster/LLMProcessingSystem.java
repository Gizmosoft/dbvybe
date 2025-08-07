package com.dbVybe.app.cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Receive;
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
    
    public LLMProcessingSystem() {
        super("LLMProcessingSystem", "application-llm-node.conf");
    }
    
    @Override
    protected Behavior<Void> createRootBehavior() {
        return Behaviors.setup(context -> {
            logger.info("Creating LLMProcessingSystem root behavior");
            
            // Create the main supervisor actor for LLM processing
            ActorRef<LLMProcessingSupervisor.Command> supervisor = 
                context.spawn(LLMProcessingSupervisor.create(), "llm-processing-supervisor");
            
            logger.info("LLMProcessingSystem actor hierarchy created");
            
            return Behaviors.empty();
        });
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
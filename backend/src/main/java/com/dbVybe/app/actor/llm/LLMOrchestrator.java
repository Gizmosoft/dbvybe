package com.dbVybe.app.actor.llm;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.ActorRef;
import com.dbVybe.app.service.llm.LLMService;
import com.dbVybe.app.service.agent.NLPAgent;
import com.dbVybe.app.service.agent.QueryExecutionAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM Orchestrator - Coordinates LLM processing in the unified system
 */
public class LLMOrchestrator extends AbstractBehavior<LLMOrchestrator.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMOrchestrator.class);
    
    private final LLMService llmService;
    private final NLPAgent nlpAgent;
    private final QueryExecutionAgent queryExecutionAgent;
    private final ActorRef<QueryGenerationActor.Command> queryGenerationActor;
    private final ActorRef<QueryExecutorActor.Command> queryExecutorActor;
    
    public interface Command {}
    
    public static class ProcessChatMessage implements Command {
        private final String message;
        private final String userId;
        private final ActorRef<LLMActor.ProcessMessageResponse> replyTo;
        
        public ProcessChatMessage(String message, String userId, ActorRef<LLMActor.ProcessMessageResponse> replyTo) {
            this.message = message;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getMessage() { return message; }
        public String getUserId() { return userId; }
        public ActorRef<LLMActor.ProcessMessageResponse> getReplyTo() { return replyTo; }
    }
    
    public static class ProcessChatMessageDirect implements Command {
        private final String message;
        private final String userId;
        private final ActorRef<LLMActor.ProcessMessageResponse> replyTo;
        
        public ProcessChatMessageDirect(String message, String userId, ActorRef<LLMActor.ProcessMessageResponse> replyTo) {
            this.message = message;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getMessage() { return message; }
        public String getUserId() { return userId; }
        public ActorRef<LLMActor.ProcessMessageResponse> getReplyTo() { return replyTo; }
    }
    
    public static Behavior<Command> create(LLMService llmService, NLPAgent nlpAgent, QueryExecutionAgent queryExecutionAgent) {
        return Behaviors.setup(context -> new LLMOrchestrator(context, llmService, nlpAgent, queryExecutionAgent));
    }
    
    private LLMOrchestrator(akka.actor.typed.javadsl.ActorContext<Command> context, 
                           LLMService llmService, NLPAgent nlpAgent, QueryExecutionAgent queryExecutionAgent) {
        super(context);
        this.llmService = llmService;
        this.nlpAgent = nlpAgent;
        this.queryExecutionAgent = queryExecutionAgent;
        
        // Create child actors
        this.queryGenerationActor = context.spawn(QueryGenerationActor.create(nlpAgent), "query-generation-actor");
        this.queryExecutorActor = context.spawn(QueryExecutorActor.create(queryExecutionAgent), "query-executor-actor");
        
        logger.info("LLMOrchestrator created with AI Agent integration");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ProcessChatMessage.class, this::onProcessChatMessage)
            .onMessage(ProcessChatMessageDirect.class, this::onProcessChatMessageDirect)
            .build();
    }
    
    private Behavior<Command> onProcessChatMessage(ProcessChatMessage command) {
        logger.debug("Processing chat message: {}", command.getMessage());
        
        // Create a temporary LLMActor to handle this request
        ActorRef<LLMActor.Command> llmActor = getContext().spawn(
            LLMActor.create(llmService, nlpAgent, queryGenerationActor), 
            "llm-actor-" + System.currentTimeMillis()
        );
        
        // Forward the message to the LLMActor
        llmActor.tell(new LLMActor.ProcessMessage(command.getMessage(), command.getUserId(), "session-" + System.currentTimeMillis(), command.getReplyTo()));
        
        return this;
    }
    
    private Behavior<Command> onProcessChatMessageDirect(ProcessChatMessageDirect command) {
        logger.debug("Processing direct chat message: {}", command.getMessage());
        
        // Create a temporary LLMActor to handle this request
        ActorRef<LLMActor.Command> llmActor = getContext().spawn(
            LLMActor.create(llmService, nlpAgent, queryGenerationActor), 
            "llm-actor-" + System.currentTimeMillis()
        );
        
        // Forward the message directly to the LLMActor
        llmActor.tell(new LLMActor.ProcessMessage(command.getMessage(), command.getUserId(), "session-" + System.currentTimeMillis(), command.getReplyTo()));
        
        return this;
    }
}

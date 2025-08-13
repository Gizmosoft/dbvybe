package com.dbVybe.app.cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import com.dbVybe.app.actor.security.SecurityActor;
import com.dbVybe.app.actor.session.UserSessionManager;
import com.dbVybe.app.actor.database.DatabaseCommunicationManager;
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import com.dbVybe.app.actor.analysis.VectorizationActor;
import com.dbVybe.app.actor.analysis.GraphActor;
import com.dbVybe.app.actor.llm.QueryExecutorActor;
import com.dbVybe.app.service.llm.GroqLLMService;
import com.dbVybe.app.service.agent.NLPAgent;
import com.dbVybe.app.service.agent.DatabaseSchemaAgent;
import com.dbVybe.app.service.agent.VectorAnalysisAgent;
import com.dbVybe.app.service.agent.GraphAnalysisAgent;
import com.dbVybe.app.service.agent.QueryExecutionAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Spring component responsible for managing the Akka cluster nodes
 */
@Component
public class ClusterManager {
    
    private DatabaseExplorationSystem coreServicesNode;
    private LLMProcessingSystem llmProcessingNode;
    private DataAnalysisSystem dataAnalysisNode;
    
    private final GroqLLMService groqLLMService;
    private final NLPAgent nlpAgent;
    private final DatabaseSchemaAgent databaseSchemaAgent;
    private final VectorAnalysisAgent vectorAnalysisAgent;
    private final GraphAnalysisAgent graphAnalysisAgent;
    private final QueryExecutionAgent queryExecutionAgent;
    
    @Autowired
    public ClusterManager(GroqLLMService groqLLMService, NLPAgent nlpAgent, DatabaseSchemaAgent databaseSchemaAgent,
                         VectorAnalysisAgent vectorAnalysisAgent, GraphAnalysisAgent graphAnalysisAgent,
                         QueryExecutionAgent queryExecutionAgent) {
        this.groqLLMService = groqLLMService;
        this.nlpAgent = nlpAgent;
        this.databaseSchemaAgent = databaseSchemaAgent;
        this.vectorAnalysisAgent = vectorAnalysisAgent;
        this.graphAnalysisAgent = graphAnalysisAgent;
        this.queryExecutionAgent = queryExecutionAgent;
    }
    
    @PostConstruct
    public void start() {
        System.out.println("Starting Akka Cluster nodes...");
        
        // Start Core Services Node (Node 1)
        coreServicesNode = new DatabaseExplorationSystem();
        coreServicesNode.start();
        
        // Start LLM Processing Node (Node 2) with all AI Agent integration
        llmProcessingNode = new LLMProcessingSystem(groqLLMService, nlpAgent, queryExecutionAgent);
        llmProcessingNode.start();
        
        // Start Data Analysis Node (Node 3) with all AI Agents integration
        dataAnalysisNode = new DataAnalysisSystem(databaseSchemaAgent, vectorAnalysisAgent, graphAnalysisAgent);
        dataAnalysisNode.start();
        
        // Wire up cross-node communication: DatabaseCommunicationManager → SchemaAnalysisActor
        try {
            if (dataAnalysisNode.getSchemaAnalysisActor() != null) {
                coreServicesNode.setSchemaAnalysisActor(dataAnalysisNode.getSchemaAnalysisActor());
                System.out.println("Schema analysis integration configured successfully!");
            }
            
            // Wire up analysis agents for cleanup operations
            ActorRef<DatabaseCommunicationManager.Command> dbCommManager = coreServicesNode.getDatabaseCommunicationManager();
            dbCommManager.tell(new DatabaseCommunicationManager.SetAnalysisAgents(databaseSchemaAgent, graphAnalysisAgent));
            System.out.println("Analysis agents configured for cleanup operations!");
            
        } catch (Exception e) {
            System.err.println("Failed to configure analysis integrations: " + e.getMessage());
        }
        
        System.out.println("All Akka Cluster nodes started successfully!");
    }
    
    @PreDestroy
    public void stop() {
        System.out.println("Stopping Akka Cluster nodes...");
        
        if (coreServicesNode != null) {
            coreServicesNode.stop();
        }
        
        if (llmProcessingNode != null) {
            llmProcessingNode.stop();
        }
        
        if (dataAnalysisNode != null) {
            dataAnalysisNode.stop();
        }
        
        System.out.println("All Akka Cluster nodes stopped successfully!");
    }
    
    /**
     * Get SecurityActor from Core Services Node
     */
    public ActorRef<SecurityActor.Command> getSecurityActor() {
        if (coreServicesNode != null) {
            return coreServicesNode.getSecurityActor();
        }
        throw new IllegalStateException("Core Services Node not started");
    }
    
    /**
     * Get UserSessionManager from Core Services Node
     */
    public ActorRef<UserSessionManager.Command> getUserSessionManager() {
        if (coreServicesNode != null) {
            return coreServicesNode.getUserSessionManager();
        }
        throw new IllegalStateException("Core Services Node not started");
    }
    
    /**
     * Get DatabaseCommunicationManager from Core Services Node
     */
    public ActorRef<DatabaseCommunicationManager.Command> getDatabaseCommunicationManager() {
        if (coreServicesNode != null) {
            return coreServicesNode.getDatabaseCommunicationManager();
        }
        throw new IllegalStateException("Core Services Node not started");
    }
    
    /**
     * Get Akka Scheduler for AskPattern operations
     */
    public Scheduler getScheduler() {
        if (coreServicesNode != null) {
            return coreServicesNode.getScheduler();
        }
        throw new IllegalStateException("Core Services Node not started");
    }
    
    /**
     * Get LLMOrchestrator from LLM Processing Node
     */
    public ActorRef<LLMProcessingSystem.LLMOrchestrator.Command> getLLMOrchestrator() {
        if (llmProcessingNode != null) {
            return llmProcessingNode.getLLMOrchestrator();
        }
        throw new IllegalStateException("LLM Processing Node not started");
    }
    
    /**
     * Get LLM Processing Node Scheduler
     */
    public Scheduler getLLMScheduler() {
        if (llmProcessingNode != null) {
            return llmProcessingNode.getScheduler();
        }
        throw new IllegalStateException("LLM Processing Node not started");
    }
    
    /**
     * Get SchemaAnalysisActor from Data Analysis Node
     */
    public ActorRef<com.dbVybe.app.actor.analysis.SchemaAnalysisActor.Command> getSchemaAnalysisActor() {
        if (dataAnalysisNode != null) {
            return dataAnalysisNode.getSchemaAnalysisActor();
        }
        throw new IllegalStateException("Data Analysis Node not started");
    }
    
    /**
     * Get Data Analysis Node Scheduler
     */
    public Scheduler getDataAnalysisScheduler() {
        if (dataAnalysisNode != null) {
            return dataAnalysisNode.getScheduler();
        }
        throw new IllegalStateException("Data Analysis Node not started");
    }
    
    /**
     * Get VectorizationActor from Data Analysis Node
     */
    public ActorRef<com.dbVybe.app.actor.analysis.VectorizationActor.Command> getVectorizationActor() {
        if (dataAnalysisNode != null) {
            return dataAnalysisNode.getVectorizationActor();
        }
        throw new IllegalStateException("Data Analysis Node not started");
    }
    
    /**
     * Get GraphActor from Data Analysis Node
     */
    public ActorRef<com.dbVybe.app.actor.analysis.GraphActor.Command> getGraphActor() {
        if (dataAnalysisNode != null) {
            return dataAnalysisNode.getGraphActor();
        }
        throw new IllegalStateException("Data Analysis Node not started");
    }
    
    /**
     * Get QueryExecutorActor from LLM Processing Node
     */
    public ActorRef<com.dbVybe.app.actor.llm.QueryExecutorActor.Command> getQueryExecutorActor() {
        if (llmProcessingNode != null) {
            return llmProcessingNode.getQueryExecutorActor();
        }
        throw new IllegalStateException("LLM Processing Node not started");
    }
    
    /**
     * Get LLM Processing Node Scheduler
     */
    public Scheduler getLLMProcessingScheduler() {
        if (llmProcessingNode != null) {
            return llmProcessingNode.getScheduler();
        }
        throw new IllegalStateException("LLM Processing Node not started");
    }
} 
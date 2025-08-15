package com.dbVybe.app.cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import com.dbVybe.app.service.llm.GroqLLMService;
import com.dbVybe.app.service.agent.NLPAgent;
import com.dbVybe.app.service.agent.DatabaseSchemaAgent;
import com.dbVybe.app.service.agent.VectorAnalysisAgent;
import com.dbVybe.app.service.agent.GraphAnalysisAgent;
import com.dbVybe.app.service.agent.QueryExecutionAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Spring component responsible for managing the Akka cluster nodes
 */
@Component
public class ClusterManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterManager.class);
    
    // Core Services Node (DatabaseExplorationSystem)
    private DatabaseExplorationSystem coreServicesNode;
    
    // LLM Processing Node (LLMProcessingSystem)
    private LLMProcessingSystem llmProcessingNode;
    
    // Data Analysis Node (DataAnalysisSystem)
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
        logger.info("Starting 3-node Akka cluster system...");
        
        try {
            // Start Core Services Node (DatabaseExplorationSystem)
            logger.info("Starting Core Services Node (DatabaseExplorationSystem)...");
            coreServicesNode = new DatabaseExplorationSystem();
            coreServicesNode.start();
            
            // Start Data Analysis Node (DataAnalysisSystem)
            logger.info("Starting Data Analysis Node (DataAnalysisSystem)...");
            dataAnalysisNode = new DataAnalysisSystem(databaseSchemaAgent, vectorAnalysisAgent, graphAnalysisAgent);
            dataAnalysisNode.start();
            
            // Start LLM Processing Node (LLMProcessingSystem)
            logger.info("Starting LLM Processing Node (LLMProcessingSystem)...");
            llmProcessingNode = new LLMProcessingSystem(groqLLMService, nlpAgent, queryExecutionAgent);
            llmProcessingNode.start();
            
            // Wire up cross-node communication
            setupCrossNodeCommunication();
            
            logger.info("3-node Akka cluster system started successfully!");
            
        } catch (Exception e) {
            logger.error("Failed to start 3-node Akka cluster system: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start Akka cluster", e);
        }
    }
    
    private void setupCrossNodeCommunication() {
        logger.info("Setting up cross-node actor communication...");
        
        try {
            // Wire DatabaseCommunicationManager (Core Services) to SchemaAnalysisActor (Data Analysis)
            if (coreServicesNode != null && dataAnalysisNode != null) {
                ActorRef<com.dbVybe.app.actor.analysis.SchemaAnalysisActor.Command> schemaAnalysisActor = 
                    dataAnalysisNode.getSchemaAnalysisActor();
                
                if (schemaAnalysisActor != null) {
                    coreServicesNode.setSchemaAnalysisActor(schemaAnalysisActor);
                    logger.info("Wired SchemaAnalysisActor from Data Analysis Node to DatabaseCommunicationManager in Core Services Node");
                }
            }
            
            // Wire analysis agents for cleanup operations in DatabaseCommunicationManager
            if (coreServicesNode != null) {
                ActorRef<com.dbVybe.app.actor.database.DatabaseCommunicationManager.Command> dbCommManager = 
                    coreServicesNode.getDatabaseCommunicationManager();
                
                if (dbCommManager != null) {
                    dbCommManager.tell(new com.dbVybe.app.actor.database.DatabaseCommunicationManager.SetAnalysisAgents(
                        databaseSchemaAgent, graphAnalysisAgent));
                    logger.info("Configured analysis agents for cleanup operations in DatabaseCommunicationManager");
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to setup cross-node communication: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void stop() {
        logger.info("Stopping 3-node Akka cluster system...");
        
        try {
            if (llmProcessingNode != null) {
                llmProcessingNode.stop();
            }
            
            if (dataAnalysisNode != null) {
                dataAnalysisNode.stop();
            }
            
            if (coreServicesNode != null) {
                coreServicesNode.stop();
            }
            
            logger.info("3-node Akka cluster system stopped");
            
        } catch (Exception e) {
            logger.error("Error stopping Akka cluster: {}", e.getMessage(), e);
        }
    }
    
    // Getter methods for Core Services Node
    public ActorRef<com.dbVybe.app.actor.security.SecurityActor.Command> getSecurityActor() {
        return coreServicesNode != null ? coreServicesNode.getSecurityActor() : null;
    }
    
    public ActorRef<com.dbVybe.app.actor.session.UserSessionManager.Command> getUserSessionManager() {
        return coreServicesNode != null ? coreServicesNode.getUserSessionManager() : null;
    }
    
    public ActorRef<com.dbVybe.app.actor.database.DatabaseCommunicationManager.Command> getDatabaseCommunicationManager() {
        return coreServicesNode != null ? coreServicesNode.getDatabaseCommunicationManager() : null;
    }
    
    // Getter methods for Data Analysis Node
    public ActorRef<com.dbVybe.app.actor.analysis.SchemaAnalysisActor.Command> getSchemaAnalysisActor() {
        return dataAnalysisNode != null ? dataAnalysisNode.getSchemaAnalysisActor() : null;
    }
    
    public ActorRef<com.dbVybe.app.actor.analysis.VectorizationActor.Command> getVectorizationActor() {
        return dataAnalysisNode != null ? dataAnalysisNode.getVectorizationActor() : null;
    }
    
    public ActorRef<com.dbVybe.app.actor.analysis.GraphActor.Command> getGraphActor() {
        return dataAnalysisNode != null ? dataAnalysisNode.getGraphActor() : null;
    }
    
    // Getter methods for LLM Processing Node
    public ActorRef<com.dbVybe.app.actor.llm.QueryExecutorActor.Command> getQueryExecutorActor() {
        return llmProcessingNode != null ? llmProcessingNode.getQueryExecutorActor() : null;
    }
    
    public ActorRef<com.dbVybe.app.actor.llm.LLMOrchestrator.Command> getLLMOrchestrator() {
        return llmProcessingNode != null ? llmProcessingNode.getLLMOrchestrator() : null;
    }
    
    // Scheduler getters for different nodes
    public Scheduler getCoreServicesScheduler() {
        return coreServicesNode != null ? coreServicesNode.getScheduler() : null;
    }
    
    public Scheduler getDataAnalysisScheduler() {
        return dataAnalysisNode != null ? dataAnalysisNode.getScheduler() : null;
    }
    
    public Scheduler getLLMProcessingScheduler() {
        return llmProcessingNode != null ? llmProcessingNode.getScheduler() : null;
    }
    
    // Convenience method for unified scheduler (for backward compatibility)
    public Scheduler getScheduler() {
        // Default to core services scheduler
        return getCoreServicesScheduler();
    }
} 
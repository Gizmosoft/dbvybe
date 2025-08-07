package com.dbVybe.app.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Cluster Manager to coordinate all three cluster nodes
 */
@Component
public class ClusterManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterManager.class);
    
    private DatabaseExplorationSystem databaseExplorationSystem;
    private LLMProcessingSystem llmProcessingSystem;
    private DataAnalysisSystem dataAnalysisSystem;
    
    @PostConstruct
    public void startCluster() {
        logger.info("Starting Akka Cluster with 3 nodes...");
        
        try {
            // Start Node 1: Core Services Node
            databaseExplorationSystem = new DatabaseExplorationSystem();
            databaseExplorationSystem.start();
            logger.info("Node 1 (DatabaseExplorationSystem) started successfully");
            
            // Start Node 2: LLM Processing Node
            llmProcessingSystem = new LLMProcessingSystem();
            llmProcessingSystem.start();
            logger.info("Node 2 (LLMProcessingSystem) started successfully");
            
            // Start Node 3: Data Analysis Node
            dataAnalysisSystem = new DataAnalysisSystem();
            dataAnalysisSystem.start();
            logger.info("Node 3 (DataAnalysisSystem) started successfully");
            
            logger.info("All cluster nodes started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start cluster nodes", e);
            throw new RuntimeException("Cluster startup failed", e);
        }
    }
    
    @PreDestroy
    public void stopCluster() {
        logger.info("Stopping Akka Cluster...");
        
        try {
            if (dataAnalysisSystem != null) {
                dataAnalysisSystem.stop();
                logger.info("Node 3 (DataAnalysisSystem) stopped");
            }
            
            if (llmProcessingSystem != null) {
                llmProcessingSystem.stop();
                logger.info("Node 2 (LLMProcessingSystem) stopped");
            }
            
            if (databaseExplorationSystem != null) {
                databaseExplorationSystem.stop();
                logger.info("Node 1 (DatabaseExplorationSystem) stopped");
            }
            
            logger.info("All cluster nodes stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error stopping cluster nodes", e);
        }
    }
    
    /**
     * Get the Database Exploration System
     */
    public DatabaseExplorationSystem getDatabaseExplorationSystem() {
        return databaseExplorationSystem;
    }
    
    /**
     * Get the LLM Processing System
     */
    public LLMProcessingSystem getLLMProcessingSystem() {
        return llmProcessingSystem;
    }
    
    /**
     * Get the Data Analysis System
     */
    public DataAnalysisSystem getDataAnalysisSystem() {
        return dataAnalysisSystem;
    }
    
    /**
     * Check if all nodes are running
     */
    public boolean isClusterHealthy() {
        return databaseExplorationSystem != null && 
               llmProcessingSystem != null && 
               dataAnalysisSystem != null;
    }
} 
package com.dbVybe.app.cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import com.dbVybe.app.actor.security.SecurityActor;
import com.dbVybe.app.actor.session.UserSessionManager;
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
    
    @Autowired
    public ClusterManager() {
        // Constructor
    }
    
    @PostConstruct
    public void start() {
        System.out.println("Starting Akka Cluster nodes...");
        
        // Start Core Services Node (Node 1)
        coreServicesNode = new DatabaseExplorationSystem();
        coreServicesNode.start();
        
        // Start LLM Processing Node (Node 2)
        llmProcessingNode = new LLMProcessingSystem();
        llmProcessingNode.start();
        
        // Start Data Analysis Node (Node 3)
        dataAnalysisNode = new DataAnalysisSystem();
        dataAnalysisNode.start();
        
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
    public ActorRef<com.dbVybe.app.actor.database.DatabaseCommunicationManager.Command> getDatabaseCommunicationManager() {
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
} 
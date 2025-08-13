package com.dbVybe.app.cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.dbVybe.app.actor.database.DatabaseCommunicationManager;
import com.dbVybe.app.actor.security.SecurityActor;
import com.dbVybe.app.actor.session.UserSessionManager;
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core Services Node - DatabaseExplorationSystem
 * Handles user authentication, session management, and database communication
 */
public class DatabaseExplorationSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseExplorationSystem.class);
    
    private ActorSystem<Object> system;
    private ActorRef<DatabaseExplorationSupervisor.Command> supervisor;
    private ActorRef<SecurityActor.Command> securityActor;
    private ActorRef<UserSessionManager.Command> userSessionManager;
    private ActorRef<DatabaseCommunicationManager.Command> databaseCommunicationManager;
    private ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor;
    
    public void start() {
        logger.info("Starting DatabaseExplorationSystem (Core Services Node)...");
        
        Behavior<Object> rootBehavior = Behaviors.setup(context -> {
            // Create the supervisor actor
            supervisor = context.spawn(DatabaseExplorationSupervisor.create(), "database-exploration-supervisor");
            
            // Get actor references from supervisor
            securityActor = context.spawn(SecurityActor.create(), "security-actor");
            userSessionManager = context.spawn(UserSessionManager.create(), "user-session-manager");
            databaseCommunicationManager = context.spawn(DatabaseCommunicationManager.create(), "database-communication-manager");
            
            logger.info("DatabaseExplorationSystem started successfully");
            return Behaviors.empty();
        });
        
        system = ActorSystem.create(rootBehavior, "DatabaseExplorationSystem");
        
        // Start Akka Management
        startAkkaManagement();
    }
    
    public void stop() {
        logger.info("Stopping DatabaseExplorationSystem...");
        if (system != null) {
            system.terminate();
        }
    }
    
    private void startAkkaManagement() {
        // Akka Management is configured in application-core-node.conf
        logger.info("Akka Management started on port 8551");
    }
    
    /**
     * Get SecurityActor reference
     */
    public ActorRef<SecurityActor.Command> getSecurityActor() {
        return securityActor;
    }
    
    /**
     * Get UserSessionManager reference
     */
    public ActorRef<UserSessionManager.Command> getUserSessionManager() {
        return userSessionManager;
    }
    
    /**
     * Get DatabaseCommunicationManager reference
     */
    public ActorRef<DatabaseCommunicationManager.Command> getDatabaseCommunicationManager() {
        return databaseCommunicationManager;
    }
    
    /**
     * Get Akka Scheduler for AskPattern operations
     */
    public Scheduler getScheduler() {
        if (system != null) {
            return system.scheduler();
        }
        throw new IllegalStateException("ActorSystem not started");
    }
    
    /**
     * Set SchemaAnalysisActor reference for database communication integration
     */
    public void setSchemaAnalysisActor(ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor) {
        this.schemaAnalysisActor = schemaAnalysisActor;
        
        if (databaseCommunicationManager != null && schemaAnalysisActor != null) {
            logger.info("Injecting SchemaAnalysisActor reference into DatabaseCommunicationManager");
            databaseCommunicationManager.tell(new DatabaseCommunicationManager.SetSchemaAnalysisActor(schemaAnalysisActor));
        }
    }
    
    /**
     * Get DatabaseExplorationSupervisor reference
     */
    public ActorRef<DatabaseExplorationSupervisor.Command> getSupervisor() {
        return supervisor;
    }
    
    /**
     * Get SchemaAnalysisActor reference
     */
    public ActorRef<SchemaAnalysisActor.Command> getSchemaAnalysisActor() {
        return schemaAnalysisActor;
    }
    
    /**
     * DatabaseExplorationSupervisor - Root actor for Core Services Node
     */
    public static class DatabaseExplorationSupervisor extends akka.actor.typed.javadsl.AbstractBehavior<DatabaseExplorationSupervisor.Command> {
        
        public interface Command {}
        
        public static class StartExploration implements Command {}
        public static class StopExploration implements Command {}
        
        public static Behavior<Command> create() {
            return Behaviors.setup(DatabaseExplorationSupervisor::new);
        }
        
        private DatabaseExplorationSupervisor(akka.actor.typed.javadsl.ActorContext<Command> context) {
            super(context);
            logger.info("DatabaseExplorationSupervisor created");
        }
        
        @Override
        public akka.actor.typed.javadsl.Receive<Command> createReceive() {
            return newReceiveBuilder()
                .onMessage(StartExploration.class, this::onStartExploration)
                .onMessage(StopExploration.class, this::onStopExploration)
                .build();
        }
        
        private Behavior<Command> onStartExploration(StartExploration command) {
            logger.info("Starting database exploration...");
            return Behaviors.same();
        }
        
        private Behavior<Command> onStopExploration(StopExploration command) {
            logger.info("Stopping database exploration...");
            return Behaviors.same();
        }
    }
} 
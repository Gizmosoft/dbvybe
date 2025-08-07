package com.dbVybe.app.cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dbVybe.app.actor.database.DatabaseCommunicationManager;
import com.dbVybe.app.actor.database.DatabaseCommunicationManager.ConnectionResponse;
import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.dto.DatabaseConnectionResponse;

/**
 * Node 1: Core Services Node with DatabaseExplorationSystem as the ActorSystem
 * 
 * This node handles:
 * - Database connection management
 * - Query execution
 * - Schema exploration
 * - Core database operations
 */
public class DatabaseExplorationSystem extends ClusterNode {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseExplorationSystem.class);
    
    public DatabaseExplorationSystem() {
        super("DatabaseExplorationSystem", "application-core-node.conf");
    }
    
    @Override
    protected Behavior<Void> createRootBehavior() {
        return Behaviors.setup(context -> {
            logger.info("Creating DatabaseExplorationSystem root behavior");
            
            // Create the main supervisor actor for database exploration
            ActorRef<DatabaseExplorationSupervisor.Command> supervisor = 
                context.spawn(DatabaseExplorationSupervisor.create(), "database-exploration-supervisor");
            
            logger.info("DatabaseExplorationSystem actor hierarchy created");
            
            return Behaviors.empty();
        });
    }
    
    /**
     * Main supervisor actor for database exploration services
     */
    public static class DatabaseExplorationSupervisor extends AbstractBehavior<DatabaseExplorationSupervisor.Command> {
        
        public interface Command {}
        
        public static class StartExploration implements Command {
            private final DatabaseConnectionRequest request;
            private final akka.actor.typed.ActorRef<ConnectionResponse> replyTo;
            
            public StartExploration(DatabaseConnectionRequest request, akka.actor.typed.ActorRef<ConnectionResponse> replyTo) {
                this.request = request;
                this.replyTo = replyTo;
            }
            
            public DatabaseConnectionRequest getRequest() { return request; }
            public akka.actor.typed.ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
        }
        
        public static class StopExploration implements Command {
            private final String connectionId;
            private final akka.actor.typed.ActorRef<ConnectionResponse> replyTo;
            
            public StopExploration(String connectionId, akka.actor.typed.ActorRef<ConnectionResponse> replyTo) {
                this.connectionId = connectionId;
                this.replyTo = replyTo;
            }
            
            public String getConnectionId() { return connectionId; }
            public akka.actor.typed.ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
        }

        public static class ConnectionResponse implements Command {
            private final DatabaseConnectionResponse response;
            private final akka.actor.typed.ActorRef<ConnectionResponse> originalReplyTo;
            
            public ConnectionResponse(DatabaseConnectionResponse response, akka.actor.typed.ActorRef<ConnectionResponse> originalReplyTo) {
                this.response = response;
                this.originalReplyTo = originalReplyTo;
            }
            
            public DatabaseConnectionResponse getResponse() { return response; }
            public akka.actor.typed.ActorRef<ConnectionResponse> getOriginalReplyTo() { return originalReplyTo; }
        }
        
        public static Behavior<Command> create() {
            return Behaviors.setup(DatabaseExplorationSupervisor::new);
        }
        
        private final Logger logger = LoggerFactory.getLogger(DatabaseExplorationSupervisor.class);
        private akka.actor.typed.ActorRef<DatabaseCommunicationManager.Command> dbManager;
    
        
        private DatabaseExplorationSupervisor(ActorContext<Command> context) {
            super(context);
            logger.info("DatabaseExplorationSupervisor created");
            
            // Create DatabaseCommunicationManager
            dbManager = context.spawn(DatabaseCommunicationManager.create(), "database-communication-manager");
        }
            
        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                .onMessage(StartExploration.class, this::onStartExploration)
                .onMessage(StopExploration.class, this::onStopExploration)
                .onMessage(ConnectionResponse.class, this::onConnectionResponse)
                .build();
        }
        
        private Behavior<Command> onStartExploration(StartExploration command) {
            logger.info("Starting database exploration for: {}", command.getRequest().getDatabaseType());
            
            // Create message adapter to convert DatabaseCommunicationManager.ConnectionResponse to our ConnectionResponse
            ActorRef<DatabaseCommunicationManager.ConnectionResponse> adapter = 
                getContext().messageAdapter(DatabaseCommunicationManager.ConnectionResponse.class,
                    response -> new ConnectionResponse(response.getResponse(), command.getReplyTo()));
            
            // Forward to DatabaseCommunicationManager
            dbManager.tell(new DatabaseCommunicationManager.EstablishConnection(
                command.getRequest(),
                adapter
            ));
            
            return Behaviors.same();
        }
        
        private Behavior<Command> onStopExploration(StopExploration command) {
            logger.info("Stopping database exploration for connection: {}", command.getConnectionId());
            
            // Create message adapter to convert DatabaseCommunicationManager.ConnectionResponse to our ConnectionResponse
            ActorRef<DatabaseCommunicationManager.ConnectionResponse> adapter = 
                getContext().messageAdapter(DatabaseCommunicationManager.ConnectionResponse.class,
                    response -> new ConnectionResponse(response.getResponse(), command.getReplyTo()));
            
            // Forward to DatabaseCommunicationManager
            dbManager.tell(new DatabaseCommunicationManager.CloseConnection(
                command.getConnectionId(),
                adapter
            ));
            
            return Behaviors.same();
        }

        private Behavior<Command> onConnectionResponse(ConnectionResponse command) {
            // Forward the response to the original requester
            command.getOriginalReplyTo().tell(command);
            return Behaviors.same();
        }
    }
} 
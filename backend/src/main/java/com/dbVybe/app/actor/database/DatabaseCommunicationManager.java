package com.dbVybe.app.actor.database;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.dto.DatabaseConnectionResponse;
import com.dbVybe.app.domain.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Actor responsible for managing database connections
 * Supports PostgreSQL, MySQL, and MongoDB with extensible design
 */
public class DatabaseCommunicationManager extends AbstractBehavior<DatabaseCommunicationManager.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseCommunicationManager.class);
    
    // Command interface
    public interface Command {}
    
    // Commands
    public static class EstablishConnection implements Command {
        private final DatabaseConnectionRequest request;
        private final akka.actor.typed.ActorRef<ConnectionResponse> replyTo;
        
        public EstablishConnection(DatabaseConnectionRequest request, akka.actor.typed.ActorRef<ConnectionResponse> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public DatabaseConnectionRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class TestConnection implements Command {
        private final DatabaseConnectionRequest request;
        private final akka.actor.typed.ActorRef<ConnectionResponse> replyTo;
        
        public TestConnection(DatabaseConnectionRequest request, akka.actor.typed.ActorRef<ConnectionResponse> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public DatabaseConnectionRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class CloseConnection implements Command {
        private final String connectionId;
        private final akka.actor.typed.ActorRef<ConnectionResponse> replyTo;
        
        public CloseConnection(String connectionId, akka.actor.typed.ActorRef<ConnectionResponse> replyTo) {
            this.connectionId = connectionId;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public akka.actor.typed.ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class GetConnectionStatus implements Command {
        private final String connectionId;
        private final akka.actor.typed.ActorRef<ConnectionResponse> replyTo;
        
        public GetConnectionStatus(String connectionId, akka.actor.typed.ActorRef<ConnectionResponse> replyTo) {
            this.connectionId = connectionId;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public akka.actor.typed.ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    // Response wrapper
    public static class ConnectionResponse {
        private final DatabaseConnectionResponse response;
        
        public ConnectionResponse(DatabaseConnectionResponse response) {
            this.response = response;
        }
        
        public DatabaseConnectionResponse getResponse() { return response; }
    }
    
    // Actor state
    private final Map<String, DatabaseConnection> activeConnections = new ConcurrentHashMap<>();
    
    public static Behavior<Command> create() {
        return Behaviors.setup(DatabaseCommunicationManager::new);
    }
    
    private DatabaseCommunicationManager(ActorContext<Command> context) {
        super(context);
        logger.info("DatabaseCommunicationManager created");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(EstablishConnection.class, this::onEstablishConnection)
                .onMessage(TestConnection.class, this::onTestConnection)
                .onMessage(CloseConnection.class, this::onCloseConnection)
                .onMessage(GetConnectionStatus.class, this::onGetConnectionStatus)
                .build();
    }
    
    private Behavior<Command> onEstablishConnection(EstablishConnection command) {
        DatabaseConnectionRequest request = command.getRequest();
        String connectionId = UUID.randomUUID().toString();
        
        logger.info("Establishing connection for: {} with ID: {}", request.getDatabaseType(), connectionId);
        
        try {
            // Validate request
            validateConnectionRequest(request);
            
            // Create connection based on database type
            DatabaseConnection connection = createConnection(request, connectionId);
            
            // Store connection
            activeConnections.put(connectionId, connection);
            
            // Send success response
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "SUCCESS", "Connection established successfully"
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
            
            logger.info("Connection established successfully with ID: {}", connectionId);
            
        } catch (Exception e) {
            logger.error("Failed to establish connection: {}", e.getMessage(), e);
            
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "FAILED", "Failed to establish connection: " + e.getMessage()
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onTestConnection(TestConnection command) {
        DatabaseConnectionRequest request = command.getRequest();
        String connectionId = UUID.randomUUID().toString();
        
        logger.info("Testing connection for: {}", request.getDatabaseType());
        
        try {
            // Validate request
            validateConnectionRequest(request);
            
            // Test connection without storing it
            testConnection(request, connectionId);
            
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "SUCCESS", "Connection test successful"
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
            
            logger.info("Connection test successful");
            
        } catch (Exception e) {
            logger.error("Connection test failed: {}", e.getMessage(), e);
            
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "FAILED", "Connection test failed: " + e.getMessage()
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onCloseConnection(CloseConnection command) {
        String connectionId = command.getConnectionId();
        
        logger.info("Closing connection: {}", connectionId);
        
        DatabaseConnection connection = activeConnections.remove(connectionId);
        
        if (connection != null) {
            try {
                connection.close();
                
                DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                    connectionId, "SUCCESS", "Connection closed successfully"
                );
                
                command.getReplyTo().tell(new ConnectionResponse(response));
                
                logger.info("Connection closed successfully: {}", connectionId);
                
            } catch (Exception e) {
                logger.error("Failed to close connection: {}", e.getMessage(), e);
                
                DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                    connectionId, "FAILED", "Failed to close connection: " + e.getMessage()
                );
                
                command.getReplyTo().tell(new ConnectionResponse(response));
            }
        } else {
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "FAILED", "Connection not found"
            );
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetConnectionStatus(GetConnectionStatus command) {
        String connectionId = command.getConnectionId();
        
        DatabaseConnection connection = activeConnections.get(connectionId);
        
        if (connection != null) {
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "ACTIVE", "Connection is active"
            );
            response.setRequest(connection.getRequest());
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        } else {
            DatabaseConnectionResponse response = new DatabaseConnectionResponse(
                connectionId, "NOT_FOUND", "Connection not found"
            );
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private void validateConnectionRequest(DatabaseConnectionRequest request) {
        if (request.getDatabaseType() == null || request.getDatabaseType().trim().isEmpty()) {
            throw new IllegalArgumentException("Database type is required");
        }
        
        if (request.getHost() == null || request.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Host is required");
        }
        
        if (request.getPort() <= 0) {
            throw new IllegalArgumentException("Valid port is required");
        }
        
        if (request.getDatabaseName() == null || request.getDatabaseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Database name is required");
        }
        
        // Validate database type
        try {
            DatabaseType.fromString(request.getDatabaseType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported database type: " + request.getDatabaseType());
        }
    }
    
    private DatabaseConnection createConnection(DatabaseConnectionRequest request, String connectionId) {
        DatabaseType dbType = DatabaseType.fromString(request.getDatabaseType());
        
        switch (dbType) {
            case POSTGRESQL:
                return new PostgreSQLConnection(request, connectionId);
            case MYSQL:
                return new MySQLConnection(request, connectionId);
            case MONGODB:
                return new MongoDBConnection(request, connectionId);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }
    
    private void testConnection(DatabaseConnectionRequest request, String connectionId) {
        DatabaseConnection connection = createConnection(request, connectionId);
        connection.test();
        connection.close();
    }
}
package com.dbVybe.app.actor.database;

import akka.actor.typed.Behavior;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.dto.DatabaseConnectionResponse;
import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import com.dbVybe.app.service.UserDatabaseConnectionService;
import com.dbVybe.app.service.ActorServiceLocator;
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import akka.actor.typed.PostStop;

/**
 * Actor responsible for managing user-specific database connections
 * Supports PostgreSQL, MySQL, and MongoDB with extensible design
 * Stores connection details in MySQL database for reuse
 */
public class DatabaseCommunicationManager extends AbstractBehavior<DatabaseCommunicationManager.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseCommunicationManager.class);
    
    // Command interface
    public interface Command {}
    
    // Commands
    public static class EstablishConnection implements Command {
        private final DatabaseConnectionRequest request;
        private final ActorRef<ConnectionResponse> replyTo;
        
        public EstablishConnection(DatabaseConnectionRequest request, ActorRef<ConnectionResponse> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public DatabaseConnectionRequest getRequest() { return request; }
        public ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class ConnectToSavedConnection implements Command {
        private final String connectionId;
        private final String userId;
        private final ActorRef<ConnectionResponse> replyTo;
        
        public ConnectToSavedConnection(String connectionId, String userId, ActorRef<ConnectionResponse> replyTo) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class GetUserConnections implements Command {
        private final String userId;
        private final ActorRef<UserConnectionsResponse> replyTo;
        
        public GetUserConnections(String userId, ActorRef<UserConnectionsResponse> replyTo) {
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getUserId() { return userId; }
        public ActorRef<UserConnectionsResponse> getReplyTo() { return replyTo; }
    }
    
    public static class TestConnection implements Command {
        private final DatabaseConnectionRequest request;
        private final ActorRef<ConnectionResponse> replyTo;
        
        public TestConnection(DatabaseConnectionRequest request, ActorRef<ConnectionResponse> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public DatabaseConnectionRequest getRequest() { return request; }
        public ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class CloseConnection implements Command {
        private final String connectionId;
        private final String userId;
        private final ActorRef<ConnectionResponse> replyTo;
        
        public CloseConnection(String connectionId, String userId, ActorRef<ConnectionResponse> replyTo) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class GetConnectionStatus implements Command {
        private final String connectionId;
        private final String userId;
        private final ActorRef<ConnectionResponse> replyTo;
        
        public GetConnectionStatus(String connectionId, String userId, ActorRef<ConnectionResponse> replyTo) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class DeleteSavedConnection implements Command {
        private final String connectionId;
        private final String userId;
        private final ActorRef<ConnectionResponse> replyTo;
        
        public DeleteSavedConnection(String connectionId, String userId, ActorRef<ConnectionResponse> replyTo) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public ActorRef<ConnectionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class SetSchemaAnalysisActor implements Command {
        private final ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor;
        
        public SetSchemaAnalysisActor(ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor) {
            this.schemaAnalysisActor = schemaAnalysisActor;
        }
        
        public ActorRef<SchemaAnalysisActor.Command> getSchemaAnalysisActor() { return schemaAnalysisActor; }
    }
    
    public static class SetAnalysisAgents implements Command {
        private final com.dbVybe.app.service.agent.DatabaseSchemaAgent databaseSchemaAgent;
        private final com.dbVybe.app.service.agent.GraphAnalysisAgent graphAnalysisAgent;
        
        public SetAnalysisAgents(com.dbVybe.app.service.agent.DatabaseSchemaAgent databaseSchemaAgent,
                               com.dbVybe.app.service.agent.GraphAnalysisAgent graphAnalysisAgent) {
            this.databaseSchemaAgent = databaseSchemaAgent;
            this.graphAnalysisAgent = graphAnalysisAgent;
        }
        
        public com.dbVybe.app.service.agent.DatabaseSchemaAgent getDatabaseSchemaAgent() { return databaseSchemaAgent; }
        public com.dbVybe.app.service.agent.GraphAnalysisAgent getGraphAnalysisAgent() { return graphAnalysisAgent; }
    }
    
    // Response wrapper
    public static class ConnectionResponse {
        private final DatabaseConnectionResponse response;
        
        public ConnectionResponse(DatabaseConnectionResponse response) {
            this.response = response;
        }
        
        public DatabaseConnectionResponse getResponse() { return response; }
    }
    
    // User connections response
    public static class UserConnectionsResponse {
        private final List<UserDatabaseConnection> connections;
        private final String message;
        private final boolean success;
        
        public UserConnectionsResponse(List<UserDatabaseConnection> connections, String message, boolean success) {
            this.connections = connections;
            this.message = message;
            this.success = success;
        }
        
        public List<UserDatabaseConnection> getConnections() { return connections; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }
    
    // Actor state - Active connections in memory
    private final Map<String, DatabaseConnection> activeConnections = new ConcurrentHashMap<>();
    
    // Schema analysis actor reference (injected via message)
    private ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor;
    
    // Analysis agents for cleanup operations
    private com.dbVybe.app.service.agent.DatabaseSchemaAgent databaseSchemaAgent;
    private com.dbVybe.app.service.agent.GraphAnalysisAgent graphAnalysisAgent;
    
    public static Behavior<Command> create() {
        return Behaviors.setup(DatabaseCommunicationManager::new);
    }
    
    public static Behavior<Command> create(ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor) {
        return Behaviors.setup(context -> new DatabaseCommunicationManager(context, schemaAnalysisActor));
    }
    
    private DatabaseCommunicationManager(ActorContext<Command> context) {
        super(context);
        logger.info("DatabaseCommunicationManager created - User-specific connection management enabled");
    }
    
    private DatabaseCommunicationManager(ActorContext<Command> context, ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor) {
        super(context);
        this.schemaAnalysisActor = schemaAnalysisActor;
        logger.info("DatabaseCommunicationManager created with Schema Analysis integration - User-specific connection management enabled");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(EstablishConnection.class, this::onEstablishConnection)
                .onMessage(ConnectToSavedConnection.class, this::onConnectToSavedConnection)
                .onMessage(GetUserConnections.class, this::onGetUserConnections)
                .onMessage(TestConnection.class, this::onTestConnection)
                .onMessage(CloseConnection.class, this::onCloseConnection)
                .onMessage(GetConnectionStatus.class, this::onGetConnectionStatus)
                .onMessage(DeleteSavedConnection.class, this::onDeleteSavedConnection)
                .onMessage(SetSchemaAnalysisActor.class, this::onSetSchemaAnalysisActor)
                .onMessage(SetAnalysisAgents.class, this::onSetAnalysisAgents)
                .onSignal(PostStop.class, sig -> {
                    activeConnections.values().forEach(c -> { try { c.close(); } catch (Exception ignored) {} });
                    activeConnections.clear();
                    return Behaviors.same();
                })
                .build();
    }
    
    private Behavior<Command> onEstablishConnection(EstablishConnection command) {
        DatabaseConnectionRequest request = command.getRequest();
        String connectionId = UUID.randomUUID().toString();
        
        logger.info("Establishing connection '{}' for user: {} with type: {}", 
            request.getConnectionName(), request.getUserId(), request.getDatabaseType());
        
        try {
            // Validate request
            validateConnectionRequest(request);
            
            // Check if connection name already exists for this user
            UserDatabaseConnectionService connectionService = getUserDatabaseConnectionService();
            if (connectionService != null && request.getConnectionName() != null) {
                Optional<UserDatabaseConnection> existingConnection = 
                    connectionService.findUserDatabaseConnectionByName(request.getConnectionName(), request.getUserId());
                if (existingConnection.isPresent()) {
                    DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                        "Connection name '" + request.getConnectionName() + "' already exists"
                    );
                    command.getReplyTo().tell(new ConnectionResponse(response));
                    return Behaviors.same();
                }
            }
            
            // Create connection based on database type
            DatabaseConnection connection = createConnection(request, connectionId);
            
            // Store connection in memory
            activeConnections.put(connectionId, connection);
            
            // Save connection details to database
            if (connectionService != null) {
                UserDatabaseConnection userDbConnection = new UserDatabaseConnection(
                    request.getUserId(),
                    request.getConnectionName(),
                    request.getDatabaseType(),
                    request.getHost(),
                    request.getPort(),
                    request.getDatabaseName(),
                    request.getUsername(),
                    request.getPassword()
                );
                userDbConnection.setConnectionId(connectionId);
                userDbConnection.setAdditionalProperties(request.getAdditionalProperties());
                
                boolean saved = connectionService.saveUserDatabaseConnection(userDbConnection);
                if (!saved) {
                    logger.warn("Failed to save connection details to database for connection: {}", connectionId);
                }
            }
            
            // Send success response
            DatabaseConnectionResponse response = DatabaseConnectionResponse.success(
                connectionId, "Connection '" + request.getConnectionName() + "' established and saved successfully"
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
            
            logger.info("Connection established and saved: {} for user: {}", connectionId, request.getUserId());
            
            // Trigger schema analysis for the new connection
            triggerSchemaAnalysis(connectionId, request.getUserId(), request.getDatabaseType());
            
        } catch (Exception e) {
            logger.error("Failed to establish connection for user: {}: {}", request.getUserId(), e.getMessage(), e);
            
            DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                "Failed to establish connection: " + e.getMessage()
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onDeleteSavedConnection(DeleteSavedConnection command) {
        String connectionId = command.getConnectionId();
        String userId = command.getUserId();
        
        logger.info("Deleting saved connection (soft delete) and closing active: {} for user: {}", connectionId, userId);
        
        // Close and remove any active in-memory connection
        DatabaseConnection connection = activeConnections.remove(connectionId);
        if (connection != null) {
            try {
                connection.close();
                logger.info("Active connection closed: {} for user: {}", connectionId, userId);
            } catch (Exception e) {
                logger.warn("Failed closing active connection during delete: {} user: {} - {}", connectionId, userId, e.getMessage());
            }
        }
        
        try {
            UserDatabaseConnectionService svc = getUserDatabaseConnectionService();
            if (svc == null) {
                command.getReplyTo().tell(new ConnectionResponse(
                    DatabaseConnectionResponse.failure("Connection service not available")
                ));
                return Behaviors.same();
            }
            
            // Soft delete first (best-effort)
            boolean deactivated = svc.deactivateUserDatabaseConnection(connectionId, userId);
            if (deactivated) {
                logger.info("Connection deactivated in DB: {} for user: {}", connectionId, userId);
            } else {
                logger.info("Connection not deactivated (not found or already inactive): {} for user: {}", connectionId, userId);
            }
            
            // Hard delete
            boolean deleted = svc.deleteUserDatabaseConnection(connectionId, userId);
            if (deleted) {
                // Trigger cleanup of analysis data (Qdrant and Neo4j)
                triggerAnalysisDataCleanup(connectionId, userId);
                
                command.getReplyTo().tell(new ConnectionResponse(
                    DatabaseConnectionResponse.success(connectionId, "Connection and associated analysis data deleted")
                ));
            } else {
                command.getReplyTo().tell(new ConnectionResponse(
                    DatabaseConnectionResponse.failure("Connection not found or could not be deleted")
                ));
            }
        } catch (Exception e) {
            logger.error("Failed to delete saved connection: {} for user: {}: {}", connectionId, userId, e.getMessage(), e);
            command.getReplyTo().tell(new ConnectionResponse(
                DatabaseConnectionResponse.failure("Failed to delete saved connection: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onConnectToSavedConnection(ConnectToSavedConnection command) {
        String connectionId = command.getConnectionId();
        String userId = command.getUserId();
        
        logger.info("Connecting to saved connection: {} for user: {}", connectionId, userId);
        
        try {
            UserDatabaseConnectionService connectionService = getUserDatabaseConnectionService();
            if (connectionService == null) {
                DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                    "Connection service not available"
                );
                command.getReplyTo().tell(new ConnectionResponse(response));
                return Behaviors.same();
            }
            
            // Find saved connection
            Optional<UserDatabaseConnection> savedConnectionOpt = 
                connectionService.findUserDatabaseConnection(connectionId, userId);
            
            if (savedConnectionOpt.isEmpty()) {
                DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                    "Saved connection not found"
                );
                command.getReplyTo().tell(new ConnectionResponse(response));
                return Behaviors.same();
            }
            
            UserDatabaseConnection savedConnection = savedConnectionOpt.get();
            
            // Create DatabaseConnectionRequest from saved connection
            DatabaseConnectionRequest request = new DatabaseConnectionRequest(
                savedConnection.getUserId(),
                savedConnection.getConnectionName(),
                savedConnection.getDatabaseType(),
                savedConnection.getHost(),
                savedConnection.getPort(),
                savedConnection.getDatabaseName(),
                savedConnection.getUsername(),
                savedConnection.getPassword()
            );
            request.setAdditionalProperties(savedConnection.getAdditionalProperties());
            
            // Create actual database connection
            DatabaseConnection connection = createConnection(request, connectionId);
            
            // Store connection in memory
            activeConnections.put(connectionId, connection);
            
            // Update last used timestamp
            connectionService.updateLastUsed(connectionId, userId);
            
            // Send success response
            DatabaseConnectionResponse response = DatabaseConnectionResponse.success(
                connectionId, "Connected to saved connection '" + savedConnection.getConnectionName() + "' successfully"
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
            
            logger.info("Successfully connected to saved connection: {} for user: {}", connectionId, userId);
            
        } catch (Exception e) {
            logger.error("Failed to connect to saved connection: {} for user: {}: {}", connectionId, userId, e.getMessage(), e);
            
            DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                "Failed to connect to saved connection: " + e.getMessage()
            );
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetUserConnections(GetUserConnections command) {
        String userId = command.getUserId();
        
        logger.info("Getting saved connections for user: {}", userId);
        
        try {
            UserDatabaseConnectionService connectionService = getUserDatabaseConnectionService();
            if (connectionService == null) {
                command.getReplyTo().tell(new UserConnectionsResponse(
                    List.of(), "Connection service not available", false
                ));
                return Behaviors.same();
            }
            
            List<UserDatabaseConnection> connections = connectionService.getUserDatabaseConnections(userId);
            
            command.getReplyTo().tell(new UserConnectionsResponse(
                connections, "Found " + connections.size() + " saved connections", true
            ));
            
            logger.info("Retrieved {} saved connections for user: {}", connections.size(), userId);
            
        } catch (Exception e) {
            logger.error("Failed to get user connections for user: {}: {}", userId, e.getMessage(), e);
            
            command.getReplyTo().tell(new UserConnectionsResponse(
                List.of(), "Failed to get user connections: " + e.getMessage(), false
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onTestConnection(TestConnection command) {
        DatabaseConnectionRequest request = command.getRequest();
        String testConnectionId = UUID.randomUUID().toString();
        
        logger.info("Testing connection for user: {} with type: {}", request.getUserId(), request.getDatabaseType());
        
        try {
            // Validate request
            validateConnectionRequest(request);
            
            // Test connection without storing it
            testConnection(request, testConnectionId);
            
            DatabaseConnectionResponse response = DatabaseConnectionResponse.success(
                testConnectionId, "Connection test successful"
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
            
            logger.info("Connection test successful for user: {}", request.getUserId());
            
        } catch (Exception e) {
            logger.error("Connection test failed for user: {}: {}", request.getUserId(), e.getMessage(), e);
            
            DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                "Connection test failed: " + e.getMessage()
            );
            response.setRequest(request);
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onCloseConnection(CloseConnection command) {
        String connectionId = command.getConnectionId();
        String userId = command.getUserId();
        
        logger.info("Closing connection: {} for user: {}", connectionId, userId);
        
        DatabaseConnection connection = activeConnections.remove(connectionId);
        
        if (connection != null) {
            try {
                connection.close();
                logger.info("Connection closed successfully: {} for user: {}", connectionId, userId);
            } catch (Exception e) {
                logger.error("Failed to close connection: {} for user: {}: {}", connectionId, userId, e.getMessage(), e);
                DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                    "Failed to close connection: " + e.getMessage()
                );
                command.getReplyTo().tell(new ConnectionResponse(response));
                // Continue to attempt deactivation even if close failed
            }
        } else {
            logger.info("Active connection not found in memory for: {} user: {}. Proceeding to soft-delete if present in DB.", connectionId, userId);
        }
        
        // Soft-delete saved record
        try {
            UserDatabaseConnectionService svc = getUserDatabaseConnectionService();
            if (svc == null) {
                DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                    "Connection service not available"
                );
                command.getReplyTo().tell(new ConnectionResponse(response));
                return Behaviors.same();
            }
            boolean deactivated = svc.deactivateUserDatabaseConnection(connectionId, userId);
            if (deactivated) {
                DatabaseConnectionResponse response = DatabaseConnectionResponse.success(
                    connectionId, "Connection closed and deactivated"
                );
                command.getReplyTo().tell(new ConnectionResponse(response));
            } else {
                DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                    "Connection not found or already inactive"
                );
                command.getReplyTo().tell(new ConnectionResponse(response));
            }
        } catch (Exception e) {
            logger.error("Failed to deactivate connection in DB: {} for user: {}: {}", connectionId, userId, e.getMessage(), e);
            DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                "Failed to deactivate connection: " + e.getMessage()
            );
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetConnectionStatus(GetConnectionStatus command) {
        String connectionId = command.getConnectionId();
        
        DatabaseConnection connection = activeConnections.get(connectionId);
        
        if (connection != null) {
            DatabaseConnectionResponse response = DatabaseConnectionResponse.success(
                connectionId, "Connection is active"
            );
            response.setRequest(connection.getRequest());
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        } else {
            DatabaseConnectionResponse response = DatabaseConnectionResponse.failure(
                "Active connection not found"
            );
            
            command.getReplyTo().tell(new ConnectionResponse(response));
        }
        
        return Behaviors.same();
    }
    
    private UserDatabaseConnectionService getUserDatabaseConnectionService() {
        return ActorServiceLocator.getUserDatabaseConnectionService();
    }
    
    private void validateConnectionRequest(DatabaseConnectionRequest request) {
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (request.getConnectionName() == null || request.getConnectionName().trim().isEmpty()) {
            throw new IllegalArgumentException("Connection name is required");
        }
        
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
    
    private Behavior<Command> onSetSchemaAnalysisActor(SetSchemaAnalysisActor command) {
        logger.info("Setting SchemaAnalysisActor reference");
        this.schemaAnalysisActor = command.getSchemaAnalysisActor();
        return Behaviors.same();
    }
    
    private Behavior<Command> onSetAnalysisAgents(SetAnalysisAgents command) {
        logger.info("Setting analysis agents for cleanup operations");
        this.databaseSchemaAgent = command.getDatabaseSchemaAgent();
        this.graphAnalysisAgent = command.getGraphAnalysisAgent();
        return Behaviors.same();
    }
    
    /**
     * Trigger schema analysis for a newly established database connection
     */
    private void triggerSchemaAnalysis(String connectionId, String userId, String databaseType) {
        if (schemaAnalysisActor == null) {
            logger.warn("SchemaAnalysisActor not available. Skipping schema analysis for connection: {}", connectionId);
            return;
        }
        
        try {
            DatabaseType dbType = DatabaseType.fromString(databaseType);
            
            logger.info("Triggering schema analysis for connection {} (user: {}, type: {})", 
                connectionId, userId, dbType);
            
            // Create a response handler for schema analysis result
            ActorRef<SchemaAnalysisActor.SchemaAnalysisResponse> responseHandler = 
                getContext().spawn(Behaviors.<SchemaAnalysisActor.SchemaAnalysisResponse>receiveMessage(response -> {
                    if (response.isSuccess()) {
                        logger.info("Schema analysis completed successfully for connection {} - Tables: {}, Embeddings: {}, Time: {}ms", 
                            response.getConnectionId(), response.getTablesAnalyzed(), 
                            response.getEmbeddingsGenerated(), response.getProcessingTimeMs());
                    } else {
                        logger.error("Schema analysis failed for connection {}: {}", 
                            response.getConnectionId(), response.getError());
                    }
                    return Behaviors.stopped();
                }), "schema-analysis-response-" + connectionId.substring(0, 8));
            
            // Send schema analysis request
            schemaAnalysisActor.tell(new SchemaAnalysisActor.AnalyzeSchema(
                connectionId, userId, dbType, responseHandler
            ));
            
        } catch (Exception e) {
            logger.error("Failed to trigger schema analysis for connection {}: {}", connectionId, e.getMessage(), e);
        }
    }
    
    /**
     * Trigger cleanup of analysis data when a database connection is permanently deleted
     */
    private void triggerAnalysisDataCleanup(String connectionId, String userId) {
        logger.info("Triggering analysis data cleanup for connection {} (user: {})", connectionId, userId);
        
        // Clean up vector embeddings in Qdrant
        if (databaseSchemaAgent != null) {
            databaseSchemaAgent.deleteSchemaEmbeddings(connectionId, userId)
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        logger.error("Error cleaning up Qdrant embeddings for connection {}: {}", 
                            connectionId, throwable.getMessage());
                    } else if (success) {
                        logger.info("Successfully cleaned up Qdrant embeddings for connection {}", connectionId);
                    } else {
                        logger.warn("Failed to clean up Qdrant embeddings for connection {}", connectionId);
                    }
                });
        } else {
            logger.warn("DatabaseSchemaAgent not available. Skipping Qdrant cleanup for connection: {}", connectionId);
        }
        
        // Clean up graph relationships in Neo4j
        if (graphAnalysisAgent != null) {
            graphAnalysisAgent.deleteConnectionGraphData(connectionId, userId)
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        logger.error("Error cleaning up Neo4j graph data for connection {}: {}", 
                            connectionId, throwable.getMessage());
                    } else if (success) {
                        logger.info("Successfully cleaned up Neo4j graph data for connection {}", connectionId);
                    } else {
                        logger.warn("Failed to clean up Neo4j graph data for connection {}", connectionId);
                    }
                });
        } else {
            logger.warn("GraphAnalysisAgent not available. Skipping Neo4j cleanup for connection: {}", connectionId);
        }
    }
}
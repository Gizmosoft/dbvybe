package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.actor.database.DatabaseCommunicationManager;
import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.dto.DatabaseConnectionResponse;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * REST Controller for user-specific database connection management using Actor-First approach
 */
@RestController
@RequestMapping("/api/database")
public class DatabaseController {
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public DatabaseController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Establish and save a user-specific database connection via DatabaseCommunicationManager
     */
    @PostMapping("/connect")
    public ResponseEntity<DatabaseConnectionResponse> establishConnection(@RequestBody DatabaseConnectionRequest request) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.EstablishConnection(request, replyTo),
                    Duration.ofSeconds(30),
                    scheduler
                );
            
            DatabaseCommunicationManager.ConnectionResponse wrapper = responseFuture.toCompletableFuture().get();
            DatabaseConnectionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("invalid connection details") || message.contains("required") || 
                    message.contains("already exists")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else if (message.contains("connection failed") || message.contains("timeout")) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DatabaseConnectionResponse.failure("Failed to establish connection: " + e.getMessage()));
        }
    }
    
    /**
     * Connect to a saved user database connection via DatabaseCommunicationManager
     */
    @PostMapping("/connect-saved")
    public ResponseEntity<DatabaseConnectionResponse> connectToSavedConnection(@RequestBody SavedConnectionRequest request) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.ConnectToSavedConnection(
                        request.getConnectionId(), request.getUserId(), replyTo),
                    Duration.ofSeconds(30),
                    scheduler
                );
            
            DatabaseCommunicationManager.ConnectionResponse wrapper = responseFuture.toCompletableFuture().get();
            DatabaseConnectionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("connection not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DatabaseConnectionResponse.failure("Failed to connect to saved connection: " + e.getMessage()));
        }
    }
    
    /**
     * Get all saved database connections for a user
     */
    @GetMapping("/connections")
    public ResponseEntity<UserConnectionsResponse> getUserConnections(@RequestParam String userId) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.UserConnectionsResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.UserConnectionsResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.GetUserConnections(userId, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            DatabaseCommunicationManager.UserConnectionsResponse response = responseFuture.toCompletableFuture().get();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(new UserConnectionsResponse(
                    response.getConnections(), response.getMessage(), true
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UserConnectionsResponse(List.of(), response.getMessage(), false));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new UserConnectionsResponse(List.of(), "Failed to get user connections: " + e.getMessage(), false));
        }
    }
    
    /**
     * Test a database connection via DatabaseCommunicationManager
     */
    @PostMapping("/test")
    public ResponseEntity<DatabaseConnectionResponse> testConnection(@RequestBody DatabaseConnectionRequest request) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.TestConnection(request, replyTo),
                    Duration.ofSeconds(30),
                    scheduler
                );
            
            DatabaseCommunicationManager.ConnectionResponse wrapper = responseFuture.toCompletableFuture().get();
            DatabaseConnectionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("invalid connection details") || message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else if (message.contains("connection failed") || message.contains("timeout")) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DatabaseConnectionResponse.failure("Failed to test connection: " + e.getMessage()));
        }
    }
    
    /**
     * Close a database connection via DatabaseCommunicationManager (Fixed with userId)
     */
    @DeleteMapping("/connect/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> closeConnection(
            @PathVariable String connectionId, 
            @RequestParam String userId) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.CloseConnection(connectionId, userId, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            DatabaseCommunicationManager.ConnectionResponse wrapper = responseFuture.toCompletableFuture().get();
            DatabaseConnectionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("connection not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DatabaseConnectionResponse.failure("Failed to close connection: " + e.getMessage()));
        }
    }
    
    /**
     * Soft-delete a saved connection and close the active in-memory connection
     */
    @DeleteMapping("/saved/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> deleteSavedConnection(
            @PathVariable String connectionId,
            @RequestParam String userId) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture =
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.DeleteSavedConnection(connectionId, userId, replyTo),
                    Duration.ofSeconds(30),
                    scheduler
                );
            
            DatabaseCommunicationManager.ConnectionResponse wrapper = responseFuture.toCompletableFuture().get();
            DatabaseConnectionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DatabaseConnectionResponse.failure("Failed to delete saved connection: " + e.getMessage()));
        }
    }
    
    /**
     * Get connection status via DatabaseCommunicationManager (Fixed with userId)
     */
    @GetMapping("/connect/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> getConnectionStatus(
            @PathVariable String connectionId, 
            @RequestParam String userId) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.GetConnectionStatus(connectionId, userId, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            DatabaseCommunicationManager.ConnectionResponse wrapper = responseFuture.toCompletableFuture().get();
            DatabaseConnectionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("connection not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DatabaseConnectionResponse.failure("Failed to get connection status: " + e.getMessage()));
        }
    }
    
    /**
     * DTO for saved connection requests
     */
    public static class SavedConnectionRequest {
        private String connectionId;
        private String userId;
        
        public SavedConnectionRequest() {}
        
        public SavedConnectionRequest(String connectionId, String userId) {
            this.connectionId = connectionId;
            this.userId = userId;
        }
        
        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
    
    /**
     * DTO for user connections response
     */
    public static class UserConnectionsResponse {
        private List<UserDatabaseConnection> connections;
        private String message;
        private boolean success;
        
        public UserConnectionsResponse(List<UserDatabaseConnection> connections, String message, boolean success) {
            this.connections = connections;
            this.message = message;
            this.success = success;
        }
        
        public List<UserDatabaseConnection> getConnections() { return connections; }
        public void setConnections(List<UserDatabaseConnection> connections) { this.connections = connections; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }
}
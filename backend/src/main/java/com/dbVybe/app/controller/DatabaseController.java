package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.actor.database.DatabaseCommunicationManager;
import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.dto.DatabaseConnectionResponse;
import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * REST Controller for database connection management using Actor-First approach
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
     * Establish a database connection via DatabaseCommunicationManager
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
                .body(DatabaseConnectionResponse.failure("Failed to establish connection: " + e.getMessage()));
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
     * Close a database connection via DatabaseCommunicationManager
     */
    @DeleteMapping("/connect/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> closeConnection(@PathVariable String connectionId) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.CloseConnection(connectionId, replyTo),
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
                } else if (message.contains("connection id is required")) {
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
     * Get connection status via DatabaseCommunicationManager
     */
    @GetMapping("/connect/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> getConnectionStatus(@PathVariable String connectionId) {
        try {
            ActorRef<DatabaseCommunicationManager.Command> dbManager = clusterManager.getDatabaseCommunicationManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<DatabaseCommunicationManager.ConnectionResponse> responseFuture = 
                AskPattern.<DatabaseCommunicationManager.Command, DatabaseCommunicationManager.ConnectionResponse>ask(
                    dbManager,
                    replyTo -> new DatabaseCommunicationManager.GetConnectionStatus(connectionId, replyTo),
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
                } else if (message.contains("connection id is required")) {
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
}
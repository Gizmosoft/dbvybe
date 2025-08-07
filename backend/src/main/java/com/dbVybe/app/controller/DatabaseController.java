package com.dbVybe.app.controller;

import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.dto.DatabaseConnectionResponse;
import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for database connection management
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
     * Establish database connection
     */
    @PostMapping("/connect")
    public ResponseEntity<DatabaseConnectionResponse> establishConnection(@RequestBody DatabaseConnectionRequest request) {
        // TODO: Implement actor communication
        // This will be implemented when we add actor communication to REST controllers
        return ResponseEntity.ok(new DatabaseConnectionResponse("temp-id", "SUCCESS", "Connection established"));
    }
    
    /**
     * Test database connection
     */
    @PostMapping("/test")
    public ResponseEntity<DatabaseConnectionResponse> testConnection(@RequestBody DatabaseConnectionRequest request) {
        // TODO: Implement actor communication
        return ResponseEntity.ok(new DatabaseConnectionResponse("temp-id", "SUCCESS", "Connection test successful"));
    }
    
    /**
     * Close database connection
     */
    @DeleteMapping("/connect/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> closeConnection(@PathVariable String connectionId) {
        // TODO: Implement actor communication
        return ResponseEntity.ok(new DatabaseConnectionResponse(connectionId, "SUCCESS", "Connection closed"));
    }
    
    /**
     * Get connection status
     */
    @GetMapping("/connect/{connectionId}")
    public ResponseEntity<DatabaseConnectionResponse> getConnectionStatus(@PathVariable String connectionId) {
        // TODO: Implement actor communication
        return ResponseEntity.ok(new DatabaseConnectionResponse(connectionId, "ACTIVE", "Connection is active"));
    }
}
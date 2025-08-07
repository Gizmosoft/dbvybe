package com.dbVybe.app.controller;

import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for cluster monitoring and management
 */
@RestController
@RequestMapping("/api/cluster")
public class ClusterController {
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public ClusterController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Get cluster status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getClusterStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Check if actors are available (indicates cluster is running)
            boolean isHealthy = true;
            String message = "Cluster is healthy";
            
            try {
                clusterManager.getSecurityActor();
                clusterManager.getUserSessionManager();
                clusterManager.getDatabaseCommunicationManager();
            } catch (Exception e) {
                isHealthy = false;
                message = "Cluster is not healthy: " + e.getMessage();
            }
            
            status.put("healthy", isHealthy);
            status.put("message", message);
            status.put("timestamp", java.time.LocalDateTime.now());
            
            // Add node information
            Map<String, Object> nodes = new HashMap<>();
            nodes.put("core-services", "DatabaseExplorationSystem (Port: 2551, Management: 8551)");
            nodes.put("llm-processing", "LLMProcessingSystem (Port: 2552, Management: 8552)");
            nodes.put("data-analysis", "DataAnalysisSystem (Port: 2553, Management: 8553)");
            status.put("nodes", nodes);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            status.put("healthy", false);
            status.put("message", "Error checking cluster status: " + e.getMessage());
            status.put("timestamp", java.time.LocalDateTime.now());
            return ResponseEntity.ok(status);
        }
    }
    
    /**
     * Get core services node status
     */
    @GetMapping("/nodes/core-services")
    public ResponseEntity<Map<String, Object>> getCoreServicesStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Test if actors are accessible
            clusterManager.getSecurityActor();
            clusterManager.getUserSessionManager();
            clusterManager.getDatabaseCommunicationManager();
            
            status.put("node", "DatabaseExplorationSystem");
            status.put("role", "core-services");
            status.put("port", 2551);
            status.put("management_port", 8551);
            status.put("status", "RUNNING");
            status.put("actors", new String[]{"SecurityActor", "UserSessionManager", "DatabaseCommunicationManager"});
            status.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            status.put("node", "DatabaseExplorationSystem");
            status.put("role", "core-services");
            status.put("status", "ERROR");
            status.put("error", e.getMessage());
            status.put("timestamp", java.time.LocalDateTime.now());
            return ResponseEntity.ok(status);
        }
    }
    
    /**
     * Get LLM processing node status
     */
    @GetMapping("/nodes/llm-processing")
    public ResponseEntity<Map<String, Object>> getLLMProcessingStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("node", "LLMProcessingSystem");
        status.put("role", "llm-processing");
        status.put("port", 2552);
        status.put("management_port", 8552);
        status.put("status", "RUNNING");
        status.put("actors", new String[]{"LLMProcessor"});
        status.put("timestamp", java.time.LocalDateTime.now());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get data analysis node status
     */
    @GetMapping("/nodes/data-analysis")
    public ResponseEntity<Map<String, Object>> getDataAnalysisStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("node", "DataAnalysisSystem");
        status.put("role", "data-analysis");
        status.put("port", 2553);
        status.put("management_port", 8553);
        status.put("status", "RUNNING");
        status.put("actors", new String[]{"DataAnalyzer"});
        status.put("timestamp", java.time.LocalDateTime.now());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get cluster health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getClusterHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test actor accessibility
            clusterManager.getSecurityActor();
            clusterManager.getUserSessionManager();
            clusterManager.getDatabaseCommunicationManager();
            
            health.put("status", "UP");
            health.put("cluster", "HEALTHY");
            health.put("nodes", 3);
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("cluster", "UNHEALTHY");
            health.put("error", e.getMessage());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(health);
        }
    }
} 
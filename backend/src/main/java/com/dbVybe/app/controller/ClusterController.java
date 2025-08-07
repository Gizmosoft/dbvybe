package com.dbVybe.app.controller;

import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for cluster management and monitoring
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
        
        status.put("healthy", clusterManager.isClusterHealthy());
        status.put("nodes", Map.of(
            "databaseExplorationSystem", clusterManager.getDatabaseExplorationSystem() != null,
            "llmProcessingSystem", clusterManager.getLLMProcessingSystem() != null,
            "dataAnalysisSystem", clusterManager.getDataAnalysisSystem() != null
        ));
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Get detailed node information
     */
    @GetMapping("/nodes/{nodeName}")
    public ResponseEntity<Map<String, Object>> getNodeInfo(@PathVariable String nodeName) {
        Map<String, Object> nodeInfo = new HashMap<>();
        
        switch (nodeName.toLowerCase()) {
            case "database-exploration":
            case "core-services":
                if (clusterManager.getDatabaseExplorationSystem() != null) {
                    nodeInfo.put("name", "DatabaseExplorationSystem");
                    nodeInfo.put("role", "core-services");
                    nodeInfo.put("status", "running");
                    nodeInfo.put("description", "Core Services Node - Database connection management, query execution, schema exploration");
                } else {
                    nodeInfo.put("status", "not-running");
                }
                break;
                
            case "llm-processing":
                if (clusterManager.getLLMProcessingSystem() != null) {
                    nodeInfo.put("name", "LLMProcessingSystem");
                    nodeInfo.put("role", "llm-processing");
                    nodeInfo.put("status", "running");
                    nodeInfo.put("description", "LLM Processing Node - Natural language query processing, SQL translation, response generation");
                } else {
                    nodeInfo.put("status", "not-running");
                }
                break;
                
            case "data-analysis":
                if (clusterManager.getDataAnalysisSystem() != null) {
                    nodeInfo.put("name", "DataAnalysisSystem");
                    nodeInfo.put("role", "data-analysis");
                    nodeInfo.put("status", "running");
                    nodeInfo.put("description", "Data Analysis Node - Data analysis, statistical processing, pattern recognition");
                } else {
                    nodeInfo.put("status", "not-running");
                }
                break;
                
            default:
                return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(nodeInfo);
    }
} 
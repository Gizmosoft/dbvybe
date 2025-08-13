package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.cluster.ClusterManager;
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import com.dbVybe.app.service.agent.DatabaseSchemaAgent;
import com.dbVybe.app.domain.model.DatabaseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * REST Controller for Schema Analysis operations
 * Provides endpoints for analyzing database schemas and searching similar schemas
 */
@RestController
@RequestMapping("/api/schema")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SchemaAnalysisController {
    
    private static final Logger logger = LoggerFactory.getLogger(SchemaAnalysisController.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60); // Longer timeout for schema analysis
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public SchemaAnalysisController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Trigger manual schema analysis for a specific database connection
     */
    @PostMapping("/analyze")
    public CompletionStage<ResponseEntity<Map<String, Object>>> analyzeSchema(
            @RequestParam String connectionId,
            @RequestParam String databaseType,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Manual schema analysis requested for connection {} (user: {}, type: {})", 
            connectionId, finalUserId, databaseType);
        
        try {
            ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor = clusterManager.getSchemaAnalysisActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            DatabaseType dbType = DatabaseType.fromString(databaseType);
            
            CompletionStage<SchemaAnalysisActor.SchemaAnalysisResponse> future = 
                AskPattern.<SchemaAnalysisActor.Command, SchemaAnalysisActor.SchemaAnalysisResponse>ask(
                    schemaAnalysisActor,
                    replyTo -> new SchemaAnalysisActor.AnalyzeSchema(connectionId, finalUserId, dbType, replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("connectionId", response.getConnectionId());
                result.put("userId", response.getUserId());
                result.put("success", response.isSuccess());
                result.put("message", response.getMessage());
                result.put("tablesAnalyzed", response.getTablesAnalyzed());
                result.put("embeddingsGenerated", response.getEmbeddingsGenerated());
                result.put("processingTimeMs", response.getProcessingTimeMs());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.isSuccess()) {
                    logger.info("Schema analysis completed successfully for connection {} - Tables: {}, Embeddings: {}", 
                        connectionId, response.getTablesAnalyzed(), response.getEmbeddingsGenerated());
                    return ResponseEntity.ok(result);
                } else {
                    result.put("error", response.getError());
                    logger.error("Schema analysis failed for connection {}: {}", connectionId, response.getError());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during schema analysis for connection {}: {}", connectionId, throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("connectionId", connectionId);
                errorResult.put("success", false);
                errorResult.put("error", "Schema analysis failed: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up schema analysis for connection {}: {}", connectionId, e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("connectionId", connectionId);
            errorResult.put("success", false);
            errorResult.put("error", "Service unavailable: " + e.getMessage());
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Search for similar schemas using vector similarity
     */
    @GetMapping("/search")
    public CompletionStage<ResponseEntity<Map<String, Object>>> searchSimilarSchemas(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Schema similarity search requested by user {} with query: '{}' (limit: {})", 
            finalUserId, query, limit);
        
        try {
            ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor = clusterManager.getSchemaAnalysisActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            CompletionStage<SchemaAnalysisActor.SimilarSchemasResponse> future = 
                AskPattern.<SchemaAnalysisActor.Command, SchemaAnalysisActor.SimilarSchemasResponse>ask(
                    schemaAnalysisActor,
                    replyTo -> new SchemaAnalysisActor.SearchSimilarSchemas(query, limit, finalUserId, replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("query", query);
                result.put("limit", limit);
                result.put("success", response.isSuccess());
                result.put("resultsCount", response.getSimilarSchemas().size());
                result.put("results", response.getSimilarSchemas());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.isSuccess()) {
                    logger.info("Found {} similar schemas for query: '{}'", response.getSimilarSchemas().size(), query);
                    return ResponseEntity.ok(result);
                } else {
                    result.put("error", response.getError());
                    logger.error("Schema similarity search failed: {}", response.getError());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during schema similarity search: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("query", query);
                errorResult.put("success", false);
                errorResult.put("error", "Search failed: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up schema similarity search: {}", e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("query", query);
            errorResult.put("success", false);
            errorResult.put("error", "Service unavailable: " + e.getMessage());
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Get schema analysis statistics
     */
    @GetMapping("/stats")
    public CompletionStage<ResponseEntity<Map<String, Object>>> getSchemaAnalysisStats() {
        
        logger.debug("Schema analysis statistics requested");
        
        try {
            ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor = clusterManager.getSchemaAnalysisActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            CompletionStage<SchemaAnalysisActor.AnalysisStatsResponse> future = 
                AskPattern.<SchemaAnalysisActor.Command, SchemaAnalysisActor.AnalysisStatsResponse>ask(
                    schemaAnalysisActor,
                    replyTo -> new SchemaAnalysisActor.GetAnalysisStats(replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", response.isSuccess());
                result.put("activeRequests", response.getActiveRequests());
                result.put("totalRequests", response.getTotalRequests());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.getAgentStats() != null) {
                    DatabaseSchemaAgent.SchemaAgentStats agentStats = response.getAgentStats();
                    Map<String, Object> agentStatsMap = new HashMap<>();
                    agentStatsMap.put("totalSchemasAnalyzed", agentStats.getTotalSchemasAnalyzed());
                    agentStatsMap.put("totalTablesProcessed", agentStats.getTotalTablesProcessed());
                    agentStatsMap.put("totalEmbeddingsGenerated", agentStats.getTotalEmbeddingsGenerated());
                    agentStatsMap.put("qdrantConnected", agentStats.isQdrantConnected());
                    
                    result.put("agentStats", agentStatsMap);
                }
                
                return ResponseEntity.ok(result);
            }).exceptionally(throwable -> {
                logger.error("Exception getting schema analysis stats: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "Failed to get stats: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up schema analysis stats request: {}", e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "Service unavailable: " + e.getMessage());
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Health check endpoint for schema analysis system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSchemaAnalysisHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test if SchemaAnalysisActor is accessible
            clusterManager.getSchemaAnalysisActor();
            
            health.put("status", "UP");
            health.put("component", "Schema Analysis System");
            health.put("node", "DataAnalysisSystem");
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("component", "Schema Analysis System");
            health.put("error", e.getMessage());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
}

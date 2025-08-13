package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.cluster.ClusterManager;
import com.dbVybe.app.actor.analysis.VectorizationActor;
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
 * REST Controller for Vectorization operations
 * Provides endpoints for semantic search and vector-based analysis
 */
@RestController
@RequestMapping("/api/vectorization")
@CrossOrigin(origins = "*", maxAge = 3600)
public class VectorizationController {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorizationController.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public VectorizationController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Perform semantic search for database schemas
     */
    @PostMapping("/search")
    public CompletionStage<ResponseEntity<Map<String, Object>>> performSemanticSearch(
            @RequestParam String query,
            @RequestParam(required = false) String connectionId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Semantic search requested: '{}' (user: {}, connection: {}, limit: {})", 
            query, finalUserId, connectionId, limit);
        
        try {
            ActorRef<VectorizationActor.Command> vectorizationActor = clusterManager.getVectorizationActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            CompletionStage<VectorizationActor.SemanticSearchResponse> future = 
                AskPattern.<VectorizationActor.Command, VectorizationActor.SemanticSearchResponse>ask(
                    vectorizationActor,
                    replyTo -> new VectorizationActor.PerformSemanticSearch(query, finalUserId, connectionId, limit, replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("query", query);
                result.put("success", response.isSuccess());
                result.put("requestId", response.getRequestId());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.isSuccess()) {
                    result.put("matches", response.getMatches());
                    result.put("totalMatches", response.getMatches().size());
                    
                    logger.info("Semantic search completed successfully - Found {} matches", response.getMatches().size());
                    return ResponseEntity.ok(result);
                } else {
                    result.put("error", response.getError());
                    logger.error("Semantic search failed: {}", response.getError());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during semantic search: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("query", query);
                errorResult.put("success", false);
                errorResult.put("error", "Search failed: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up semantic search: {}", e.getMessage());
            
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
     * Find related tables using vector similarity
     */
    @GetMapping("/related-tables")
    public CompletionStage<ResponseEntity<Map<String, Object>>> findRelatedTables(
            @RequestParam String tableName,
            @RequestParam String connectionId,
            @RequestParam(defaultValue = "10") int limit) {
        
        logger.info("Related tables search requested for: {} in connection {} (limit: {})", 
            tableName, connectionId, limit);
        
        try {
            ActorRef<VectorizationActor.Command> vectorizationActor = clusterManager.getVectorizationActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            CompletionStage<VectorizationActor.RelatedTablesResponse> future = 
                AskPattern.<VectorizationActor.Command, VectorizationActor.RelatedTablesResponse>ask(
                    vectorizationActor,
                    replyTo -> new VectorizationActor.FindRelatedTables(tableName, connectionId, limit, replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("tableName", tableName);
                result.put("connectionId", connectionId);
                result.put("success", response.isSuccess());
                result.put("requestId", response.getRequestId());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.isSuccess()) {
                    result.put("relatedTables", response.getRelatedTables());
                    result.put("totalRelated", response.getRelatedTables().size());
                    
                    logger.info("Found {} related tables for {}", response.getRelatedTables().size(), tableName);
                    return ResponseEntity.ok(result);
                } else {
                    result.put("error", response.getError());
                    logger.error("Related tables search failed: {}", response.getError());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during related tables search: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("tableName", tableName);
                errorResult.put("success", false);
                errorResult.put("error", "Search failed: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up related tables search: {}", e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("tableName", tableName);
            errorResult.put("success", false);
            errorResult.put("error", "Service unavailable: " + e.getMessage());
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Get query context for natural language processing
     */
    @PostMapping("/query-context")
    public CompletionStage<ResponseEntity<Map<String, Object>>> getQueryContext(
            @RequestParam String naturalLanguageQuery,
            @RequestParam(required = false) String connectionId,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Query context requested for: '{}' (user: {}, connection: {})", 
            naturalLanguageQuery, finalUserId, connectionId);
        
        try {
            ActorRef<VectorizationActor.Command> vectorizationActor = clusterManager.getVectorizationActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            CompletionStage<VectorizationActor.QueryContextResponse> future = 
                AskPattern.<VectorizationActor.Command, VectorizationActor.QueryContextResponse>ask(
                    vectorizationActor,
                    replyTo -> new VectorizationActor.GetQueryContext(naturalLanguageQuery, connectionId, finalUserId, replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("query", naturalLanguageQuery);
                result.put("success", response.isSuccess());
                result.put("requestId", response.getRequestId());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.isSuccess()) {
                    result.put("tableContexts", response.getTableContexts());
                    result.put("suggestedTables", response.getSuggestedTables());
                    result.put("contextCount", response.getTableContexts().size());
                    
                    logger.info("Generated query context with {} table contexts", response.getTableContexts().size());
                    return ResponseEntity.ok(result);
                } else {
                    result.put("error", response.getError());
                    logger.error("Query context generation failed: {}", response.getError());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during query context generation: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("query", naturalLanguageQuery);
                errorResult.put("success", false);
                errorResult.put("error", "Context generation failed: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up query context generation: {}", e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("query", naturalLanguageQuery);
            errorResult.put("success", false);
            errorResult.put("error", "Service unavailable: " + e.getMessage());
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Get vectorization statistics
     */
    @GetMapping("/stats")
    public CompletionStage<ResponseEntity<Map<String, Object>>> getVectorizationStats() {
        
        logger.debug("Vectorization statistics requested");
        
        try {
            ActorRef<VectorizationActor.Command> vectorizationActor = clusterManager.getVectorizationActor();
            Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
            
            CompletionStage<VectorizationActor.VectorizationStatsResponse> future = 
                AskPattern.<VectorizationActor.Command, VectorizationActor.VectorizationStatsResponse>ask(
                    vectorizationActor,
                    replyTo -> new VectorizationActor.GetVectorizationStats(replyTo),
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
                    Map<String, Object> agentStats = new HashMap<>();
                    agentStats.put("totalVectorSearches", response.getAgentStats().getTotalVectorSearches());
                    agentStats.put("totalSemanticAnalyses", response.getAgentStats().getTotalSemanticAnalyses());
                    agentStats.put("totalContextQueries", response.getAgentStats().getTotalContextQueries());
                    agentStats.put("qdrantConnected", response.getAgentStats().isQdrantConnected());
                    
                    result.put("agentStats", agentStats);
                }
                
                return ResponseEntity.ok(result);
            }).exceptionally(throwable -> {
                logger.error("Exception getting vectorization stats: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "Failed to get stats: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up vectorization stats request: {}", e.getMessage());
            
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
     * Health check endpoint for vectorization system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getVectorizationHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test if VectorizationActor is accessible
            clusterManager.getVectorizationActor();
            
            health.put("status", "UP");
            health.put("component", "Vectorization System");
            health.put("node", "DataAnalysisSystem");
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("component", "Vectorization System");
            health.put("error", e.getMessage());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
}

package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.cluster.ClusterManager;
import com.dbVybe.app.actor.llm.QueryExecutorActor;
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
 * REST Controller for Query Execution operations
 * Provides endpoints for safe database query execution
 */
@RestController
@RequestMapping("/api/query-execution")
@CrossOrigin(origins = "*", maxAge = 3600)
public class QueryExecutionController {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryExecutionController.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60); // Longer timeout for query execution
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public QueryExecutionController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Execute a database query safely
     */
    @PostMapping("/execute")
    public CompletionStage<ResponseEntity<Map<String, Object>>> executeQuery(
            @RequestBody QueryExecutionRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Query execution requested by user: {} on connection: {} (max rows: {})", 
            finalUserId, request.getConnectionId(), request.getMaxRows());
        logger.debug("Query to execute: {}", request.getQuery());
        
        try {
            ActorRef<QueryExecutorActor.Command> queryExecutorActor = clusterManager.getQueryExecutorActor();
            Scheduler scheduler = clusterManager.getLLMProcessingScheduler();
            
            CompletionStage<QueryExecutorActor.QueryExecutionResponse> future = 
                AskPattern.<QueryExecutorActor.Command, QueryExecutorActor.QueryExecutionResponse>ask(
                    queryExecutorActor,
                    replyTo -> new QueryExecutorActor.ExecuteQuery(
                        request.getQuery(), 
                        request.getConnectionId(), 
                        finalUserId, 
                        request.getMaxRows(), 
                        replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", response.isSuccess());
                result.put("requestId", response.getRequestId());
                result.put("status", response.getStatus());
                result.put("executionTimeMs", response.getExecutionTimeMs());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.isSuccess()) {
                    result.put("data", response.getData());
                    result.put("rowCount", response.getRowCount());
                    
                    logger.info("Query executed successfully - Rows: {}, Time: {}ms", 
                        response.getRowCount(), response.getExecutionTimeMs());
                    return ResponseEntity.ok(result);
                } else {
                    result.put("error", response.getError());
                    
                    // Different HTTP status codes based on error type
                    HttpStatus status = switch (response.getStatus()) {
                        case "BLOCKED" -> HttpStatus.FORBIDDEN;
                        case "CONNECTION_ERROR" -> HttpStatus.BAD_REQUEST;
                        case "SQL_ERROR" -> HttpStatus.BAD_REQUEST;
                        case "UNSUPPORTED_DB_TYPE" -> HttpStatus.NOT_IMPLEMENTED;
                        default -> HttpStatus.INTERNAL_SERVER_ERROR;
                    };
                    
                    logger.warn("Query execution failed: {} (Status: {})", response.getError(), response.getStatus());
                    return ResponseEntity.status(status).body(result);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during query execution: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "Query execution failed: " + throwable.getMessage());
                errorResult.put("status", "EXCEPTION");
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up query execution: {}", e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "Service unavailable: " + e.getMessage());
            errorResult.put("status", "SERVICE_ERROR");
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Validate query safety without executing
     */
    @PostMapping("/validate")
    public CompletionStage<ResponseEntity<Map<String, Object>>> validateQuery(
            @RequestBody QueryValidationRequest request) {
        
        logger.info("Query validation requested for: {}", request.getQuery());
        
        try {
            ActorRef<QueryExecutorActor.Command> queryExecutorActor = clusterManager.getQueryExecutorActor();
            Scheduler scheduler = clusterManager.getLLMProcessingScheduler();
            
            CompletionStage<QueryExecutorActor.QueryValidationResponse> future = 
                AskPattern.<QueryExecutorActor.Command, QueryExecutorActor.QueryValidationResponse>ask(
                    queryExecutorActor,
                    replyTo -> new QueryExecutorActor.ValidateQuery(request.getQuery(), replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("valid", response.isValid());
                result.put("requestId", response.getRequestId());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (!response.isValid()) {
                    result.put("reason", response.getReason());
                }
                
                return ResponseEntity.ok(result);
            }).exceptionally(throwable -> {
                logger.error("Exception during query validation: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("valid", false);
                errorResult.put("reason", "Validation failed: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up query validation: {}", e.getMessage());
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("valid", false);
            errorResult.put("reason", "Service unavailable: " + e.getMessage());
            errorResult.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResult)
            );
        }
    }
    
    /**
     * Get supported database types
     */
    @GetMapping("/supported-databases")
    public CompletionStage<ResponseEntity<Map<String, Object>>> getSupportedDatabases() {
        
        logger.debug("Supported databases requested");
        
        try {
            ActorRef<QueryExecutorActor.Command> queryExecutorActor = clusterManager.getQueryExecutorActor();
            Scheduler scheduler = clusterManager.getLLMProcessingScheduler();
            
            CompletionStage<QueryExecutorActor.SupportedDatabasesResponse> future = 
                AskPattern.<QueryExecutorActor.Command, QueryExecutorActor.SupportedDatabasesResponse>ask(
                    queryExecutorActor,
                    replyTo -> new QueryExecutorActor.GetSupportedDatabases(replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", response.isSuccess());
                result.put("supportedTypes", response.getSupportedTypes());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.ok(result);
            }).exceptionally(throwable -> {
                logger.error("Exception getting supported databases: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "Failed to get supported databases: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up supported databases request: {}", e.getMessage());
            
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
     * Get query execution statistics
     */
    @GetMapping("/stats")
    public CompletionStage<ResponseEntity<Map<String, Object>>> getExecutionStats() {
        
        logger.debug("Query execution statistics requested");
        
        try {
            ActorRef<QueryExecutorActor.Command> queryExecutorActor = clusterManager.getQueryExecutorActor();
            Scheduler scheduler = clusterManager.getLLMProcessingScheduler();
            
            CompletionStage<QueryExecutorActor.ExecutionStatsResponse> future = 
                AskPattern.<QueryExecutorActor.Command, QueryExecutorActor.ExecutionStatsResponse>ask(
                    queryExecutorActor,
                    replyTo -> new QueryExecutorActor.GetExecutionStats(replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(response -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", response.isSuccess());
                result.put("activeExecutions", response.getActiveExecutions());
                result.put("totalExecutions", response.getTotalExecutions());
                result.put("timestamp", java.time.LocalDateTime.now());
                
                if (response.getAgentStats() != null) {
                    Map<String, Object> agentStats = new HashMap<>();
                    agentStats.put("totalQueriesExecuted", response.getAgentStats().getTotalQueriesExecuted());
                    agentStats.put("totalQueriesBlocked", response.getAgentStats().getTotalQueriesBlocked());
                    agentStats.put("totalErrors", response.getAgentStats().getTotalErrors());
                    agentStats.put("executionsByType", response.getAgentStats().getExecutionsByType());
                    
                    result.put("agentStats", agentStats);
                }
                
                return ResponseEntity.ok(result);
            }).exceptionally(throwable -> {
                logger.error("Exception getting execution stats: {}", throwable.getMessage());
                
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("error", "Failed to get stats: " + throwable.getMessage());
                errorResult.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up execution stats request: {}", e.getMessage());
            
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
     * Health check endpoint for query execution system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getQueryExecutionHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test if QueryExecutorActor is accessible
            clusterManager.getQueryExecutorActor();
            
            health.put("status", "UP");
            health.put("component", "Query Execution System");
            health.put("node", "LLMProcessingSystem");
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("component", "Query Execution System");
            health.put("error", e.getMessage());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
    
    // Request DTOs
    
    public static class QueryExecutionRequest {
        private String query;
        private String connectionId;
        private int maxRows = 1000; // Default max rows
        
        // Constructors
        public QueryExecutionRequest() {}
        
        public QueryExecutionRequest(String query, String connectionId, int maxRows) {
            this.query = query;
            this.connectionId = connectionId;
            this.maxRows = maxRows;
        }
        
        // Getters and Setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
        
        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }
    }
    
    public static class QueryValidationRequest {
        private String query;
        
        // Constructors
        public QueryValidationRequest() {}
        
        public QueryValidationRequest(String query) {
            this.query = query;
        }
        
        // Getters and Setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }
}

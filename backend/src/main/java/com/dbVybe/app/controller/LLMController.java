package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.cluster.ClusterManager;
import com.dbVybe.app.cluster.LLMProcessingSystem;
import com.dbVybe.app.domain.dto.LLMChatRequest;
import com.dbVybe.app.domain.dto.LLMChatResponse;
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
 * REST Controller for LLM chat interface
 * Provides endpoints for chat-based interactions with the LLM processing system
 */
@RestController
@RequestMapping("/api/llm")
@CrossOrigin(origins = "*", maxAge = 3600)
public class LLMController {
    
    private static final Logger logger = LoggerFactory.getLogger(LLMController.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public LLMController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Process a chat message through the LLM system with database context
     * Supports both standard processing (with request tracking) and direct forwarding (optimized)
     * Integrates with schema analysis, vector search, and query execution
     */
    @PostMapping("/chat")
    public CompletionStage<ResponseEntity<LLMChatResponse>> processChat(
            @RequestBody LLMChatRequest request,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestParam(value = "direct", required = false, defaultValue = "false") boolean useDirect) {
        
        // Default user ID if not provided
        final String finalUserId = (userId == null || userId.trim().isEmpty()) ? "anonymous" : userId;
        
        logger.info("Received chat request from user {} using {} mode: {}", 
            finalUserId, useDirect ? "DIRECT" : "STANDARD", request.getMessage());
        
        try {
            
            // Get LLM orchestrator and scheduler
            ActorRef<LLMProcessingSystem.LLMOrchestrator.Command> llmOrchestrator = clusterManager.getLLMOrchestrator();
            Scheduler scheduler = clusterManager.getLLMScheduler();
            
            // Choose between standard processing (with request tracking) or direct forwarding
            CompletionStage<LLMProcessingSystem.LLMOrchestrator.ChatResponse> future;
            
            if (useDirect) {
                // Use direct forwarding for optimized performance
                logger.debug("Using direct forwarding for user {}", finalUserId);
                future = AskPattern.<LLMProcessingSystem.LLMOrchestrator.Command, LLMProcessingSystem.LLMOrchestrator.ChatResponse>ask(
                    llmOrchestrator,
                    replyTo -> LLMProcessingSystem.createDirectChatMessage(
                        finalUserId, 
                        request.getMessage(), 
                        request.getSessionId(), 
                        replyTo
                    ),
                    TIMEOUT,
                    scheduler
                );
            } else {
                // Use standard processing with request tracking
                logger.debug("Using standard processing with request tracking for user {}", finalUserId);
                future = AskPattern.<LLMProcessingSystem.LLMOrchestrator.Command, LLMProcessingSystem.LLMOrchestrator.ChatResponse>ask(
                    llmOrchestrator,
                    replyTo -> LLMProcessingSystem.createChatMessage(
                        finalUserId, 
                        request.getMessage(), 
                        request.getSessionId(), 
                        replyTo
                    ),
                    TIMEOUT,
                    scheduler
                );
            }
            
            // Transform actor response to HTTP response
            return future.thenApply(actorResponse -> {
                if (actorResponse.isSuccess()) {
                    LLMChatResponse response = new LLMChatResponse(
                        actorResponse.getRequestId(), // Will be null in direct mode
                        actorResponse.getResponse(),
                        request.getSessionId()
                    );
                    logger.info("Successfully processed chat request for user {} using {} mode", 
                        finalUserId, useDirect ? "DIRECT" : "STANDARD");
                    return ResponseEntity.ok(response);
                } else {
                    LLMChatResponse errorResponse = new LLMChatResponse(
                        actorResponse.getRequestId(), // Will be null in direct mode
                        actorResponse.getError(),
                        request.getSessionId(),
                        true
                    );
                    logger.error("Error processing chat request for user {} using {} mode: {}", 
                        finalUserId, useDirect ? "DIRECT" : "STANDARD", actorResponse.getError());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            }).exceptionally(throwable -> {
                logger.error("Exception during chat processing for user {}: {}", finalUserId, throwable.getMessage());
                LLMChatResponse errorResponse = new LLMChatResponse(
                    null,
                    "Internal server error: " + throwable.getMessage(),
                    request.getSessionId(),
                    true
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
            
        } catch (Exception e) {
            logger.error("Error setting up chat request for user {}: {}", finalUserId, e.getMessage());
            LLMChatResponse errorResponse = new LLMChatResponse(
                null,
                "Service unavailable: " + e.getMessage(),
                request.getSessionId(),
                true
            );
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse)
            );
        }
    }
    
    /**
     * Get LLM system status
     */
    @GetMapping("/status")
    public CompletionStage<ResponseEntity<Map<String, Object>>> getLLMStatus() {
        
        logger.debug("Checking LLM system status");
        
        try {
            ActorRef<LLMProcessingSystem.LLMOrchestrator.Command> llmOrchestrator = clusterManager.getLLMOrchestrator();
            Scheduler scheduler = clusterManager.getLLMScheduler();
            
            CompletionStage<LLMProcessingSystem.LLMOrchestrator.StatusResponse> future = 
                AskPattern.<LLMProcessingSystem.LLMOrchestrator.Command, LLMProcessingSystem.LLMOrchestrator.StatusResponse>ask(
                    llmOrchestrator,
                    replyTo -> new LLMProcessingSystem.LLMOrchestrator.GetStatus(replyTo),
                    TIMEOUT,
                    scheduler
                );
            
            return future.thenApply(statusResponse -> {
                Map<String, Object> status = new HashMap<>();
                status.put("ready", statusResponse.isReady());
                status.put("activeRequests", statusResponse.getActiveRequests());
                status.put("status", statusResponse.getStatus());
                status.put("node", "LLMProcessingSystem");
                status.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.ok(status);
            }).exceptionally(throwable -> {
                logger.error("Error getting LLM status: {}", throwable.getMessage());
                Map<String, Object> errorStatus = new HashMap<>();
                errorStatus.put("ready", false);
                errorStatus.put("status", "ERROR");
                errorStatus.put("error", throwable.getMessage());
                errorStatus.put("timestamp", java.time.LocalDateTime.now());
                
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorStatus);
            });
            
        } catch (Exception e) {
            logger.error("Error accessing LLM system: {}", e.getMessage());
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("ready", false);
            errorStatus.put("status", "UNAVAILABLE");
            errorStatus.put("error", e.getMessage());
            errorStatus.put("timestamp", java.time.LocalDateTime.now());
            
            return java.util.concurrent.CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorStatus)
            );
        }
    }
    
    /**
     * Health check endpoint for LLM system
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getLLMHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test if LLM orchestrator is accessible
            clusterManager.getLLMOrchestrator();
            
            health.put("status", "UP");
            health.put("component", "LLM Processing System");
            health.put("node", "LLMProcessingSystem");
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("component", "LLM Processing System");
            health.put("error", e.getMessage());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
}

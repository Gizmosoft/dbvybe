package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.actor.session.UserSessionManager;
import com.dbVybe.app.domain.dto.UserSessionRequest;
import com.dbVybe.app.domain.dto.UserSessionResponse;
import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * REST Controller for user session management using Actor-First approach
 */
@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserSessionController {
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public UserSessionController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * Create a new user session via UserSessionManager
     */
    @PostMapping("/create")
    public ResponseEntity<UserSessionResponse> createSession(@RequestBody UserSessionRequest request) {
        try {
            ActorRef<UserSessionManager.Command> sessionManager = clusterManager.getUserSessionManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<UserSessionManager.SessionResponse> responseFuture = 
                AskPattern.<UserSessionManager.Command, UserSessionManager.SessionResponse>ask(
                    sessionManager,
                    replyTo -> new UserSessionManager.CreateSession(request, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            UserSessionManager.SessionResponse wrapper = responseFuture.toCompletableFuture().get();
            UserSessionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("account is not active")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserSessionResponse.failure("Failed to create session: " + e.getMessage()));
        }
    }
    
    /**
     * Validate a session via UserSessionManager
     */
    @PostMapping("/validate")
    public ResponseEntity<UserSessionResponse> validateSession(@RequestBody UserSessionRequest request) {
        try {
            ActorRef<UserSessionManager.Command> sessionManager = clusterManager.getUserSessionManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<UserSessionManager.SessionResponse> responseFuture = 
                AskPattern.<UserSessionManager.Command, UserSessionManager.SessionResponse>ask(
                    sessionManager,
                    replyTo -> new UserSessionManager.ValidateSession(request.getSessionId(), replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            UserSessionManager.SessionResponse wrapper = responseFuture.toCompletableFuture().get();
            UserSessionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("session not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("session is not active") || message.contains("session has expired")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                } else if (message.contains("session id is required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserSessionResponse.failure("Failed to validate session: " + e.getMessage()));
        }
    }
    
    /**
     * Extend a session via UserSessionManager
     */
    @PostMapping("/extend")
    public ResponseEntity<UserSessionResponse> extendSession(@RequestBody UserSessionRequest request) {
        try {
            ActorRef<UserSessionManager.Command> sessionManager = clusterManager.getUserSessionManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<UserSessionManager.SessionResponse> responseFuture = 
                AskPattern.<UserSessionManager.Command, UserSessionManager.SessionResponse>ask(
                    sessionManager,
                    replyTo -> new UserSessionManager.ExtendSession(request.getSessionId(), request.getSessionDurationHours(), replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            UserSessionManager.SessionResponse wrapper = responseFuture.toCompletableFuture().get();
            UserSessionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("session not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("session is not active")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                } else if (message.contains("session id is required") || message.contains("duration must be")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserSessionResponse.failure("Failed to extend session: " + e.getMessage()));
        }
    }
    
    /**
     * Revoke a session via UserSessionManager
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<UserSessionResponse> revokeSession(@PathVariable String sessionId) {
        try {
            ActorRef<UserSessionManager.Command> sessionManager = clusterManager.getUserSessionManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<UserSessionManager.SessionResponse> responseFuture = 
                AskPattern.<UserSessionManager.Command, UserSessionManager.SessionResponse>ask(
                    sessionManager,
                    replyTo -> new UserSessionManager.RevokeSession(sessionId, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            UserSessionManager.SessionResponse wrapper = responseFuture.toCompletableFuture().get();
            UserSessionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("session not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("session id is required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserSessionResponse.failure("Failed to revoke session: " + e.getMessage()));
        }
    }
    
    /**
     * Get user sessions via UserSessionManager
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserSessionResponse> getUserSessions(@PathVariable String userId) {
        try {
            ActorRef<UserSessionManager.Command> sessionManager = clusterManager.getUserSessionManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<UserSessionManager.SessionResponse> responseFuture = 
                AskPattern.<UserSessionManager.Command, UserSessionManager.SessionResponse>ask(
                    sessionManager,
                    replyTo -> new UserSessionManager.GetUserSessions(userId, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            UserSessionManager.SessionResponse wrapper = responseFuture.toCompletableFuture().get();
            UserSessionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("user id is required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserSessionResponse.failure("Failed to get user sessions: " + e.getMessage()));
        }
    }
    
    /**
     * Clean up expired sessions via UserSessionManager
     */
    @PostMapping("/cleanup")
    public ResponseEntity<UserSessionResponse> cleanupExpiredSessions() {
        try {
            ActorRef<UserSessionManager.Command> sessionManager = clusterManager.getUserSessionManager();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<UserSessionManager.SessionResponse> responseFuture = 
                AskPattern.<UserSessionManager.Command, UserSessionManager.SessionResponse>ask(
                    sessionManager,
                    replyTo -> new UserSessionManager.CleanupExpiredSessions(),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            UserSessionManager.SessionResponse wrapper = responseFuture.toCompletableFuture().get();
            UserSessionResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UserSessionResponse.failure("Failed to cleanup expired sessions: " + e.getMessage()));
        }
    }
} 
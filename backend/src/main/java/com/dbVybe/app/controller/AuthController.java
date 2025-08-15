package com.dbVybe.app.controller;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.actor.security.SecurityActor;
import com.dbVybe.app.domain.dto.SecurityRequest;
import com.dbVybe.app.domain.dto.SecurityResponse;
import com.dbVybe.app.cluster.ClusterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * REST Controller for authentication and authorization using Actor-First approach
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public AuthController(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    /**
     * User authentication via SecurityActor
     */
    @PostMapping("/login")
    public ResponseEntity<SecurityResponse> authenticateUser(@RequestBody SecurityRequest request) {
        try {
            // Get SecurityActor from cluster
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            // Send message to SecurityActor and wait for response
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.AuthenticateUser(request, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            // Wait for response
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            // Return appropriate HTTP status based on response
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("invalid credentials") || message.contains("account is locked") || 
                    message.contains("account is not active")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                } else if (message.contains("username and password are required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Authentication failed: " + e.getMessage()));
        }
    }
    
    /**
     * User registration via SecurityActor
     */
    @PostMapping("/register")
    public ResponseEntity<SecurityResponse> registerUser(@RequestBody SecurityRequest request) {
        try {
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.RegisterUser(request, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("username already exists") || message.contains("email already exists")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
                } else if (message.contains("required") || message.contains("password must be")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else if (message.contains("failed to save")) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Registration failed: " + e.getMessage()));
        }
    }
    
    /**
     * Change password via SecurityActor
     */
    @PostMapping("/change-password")
    public ResponseEntity<SecurityResponse> changePassword(@RequestBody SecurityRequest request) {
        try {
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.ChangePassword(request, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("current password is incorrect")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                } else if (message.contains("required") || message.contains("password must be")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Password change failed: " + e.getMessage()));
        }
    }
    
    /**
     * Update user role via SecurityActor
     */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<SecurityResponse> updateUserRole(@PathVariable String userId, @RequestBody SecurityRequest request) {
        try {
            request.setUserId(userId);
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.UpdateUserRole(request, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("invalid role")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Role update failed: " + e.getMessage()));
        }
    }
    
    /**
     * Update user status via SecurityActor
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<SecurityResponse> updateUserStatus(@PathVariable String userId, @RequestBody SecurityRequest request) {
        try {
            request.setUserId(userId);
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.UpdateUserStatus(request, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("invalid status")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else if (message.contains("required")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Status update failed: " + e.getMessage()));
        }
    }
    
    /**
     * Get user by ID via SecurityActor
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<SecurityResponse> getUser(@PathVariable String userId) {
        try {
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.GetUser(userId, replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Failed to get user: " + e.getMessage()));
        }
    }
    
    /**
     * Get all users via SecurityActor
     */
    @GetMapping("/users")
    public ResponseEntity<SecurityResponse> getAllUsers() {
        try {
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.GetAllUsers(replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Failed to get users: " + e.getMessage()));
        }
    }
    
    /**
     * User logout via SecurityActor
     */
    @PostMapping("/logout")
    public ResponseEntity<SecurityResponse> logoutUser(@RequestBody SecurityRequest request) {
        try {
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.LogoutUser(request.getSessionId(), replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
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
                .body(SecurityResponse.failure("Logout failed: " + e.getMessage()));
        }
    }
    
    /**
     * Validate access via SecurityActor
     */
    @PostMapping("/validate-access")
    public ResponseEntity<SecurityResponse> validateAccess(@RequestBody SecurityRequest request) {
        try {
            ActorRef<SecurityActor.Command> securityActor = clusterManager.getSecurityActor();
            Scheduler scheduler = clusterManager.getScheduler();
            
            CompletionStage<SecurityActor.SecurityResponseWrapper> responseFuture = 
                AskPattern.<SecurityActor.Command, SecurityActor.SecurityResponseWrapper>ask(
                    securityActor,
                    replyTo -> new SecurityActor.ValidateAccess(request.getUserId(), request.getRole(), replyTo),
                    Duration.ofSeconds(10),
                    scheduler
                );
            
            SecurityActor.SecurityResponseWrapper wrapper = responseFuture.toCompletableFuture().get();
            SecurityResponse response = wrapper.getResponse();
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                String message = response.getMessage().toLowerCase();
                if (message.contains("user not found")) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                } else if (message.contains("account is not active")) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                } else if (message.contains("access denied")) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
                } else if (message.contains("invalid role")) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SecurityResponse.failure("Access validation failed: " + e.getMessage()));
        }
    }
} 
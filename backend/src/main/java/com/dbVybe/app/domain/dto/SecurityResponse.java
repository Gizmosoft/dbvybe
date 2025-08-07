package com.dbVybe.app.domain.dto;

import com.dbVybe.app.domain.model.User;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for security management responses
 */
public class SecurityResponse {
    private String userId;
    private String username;
    private String email;
    private String role;
    private String status;
    private LocalDateTime lastLoginAt;
    private String message;
    private boolean success;
    private String token;
    private List<User> users;
    private String sessionId;
    private LocalDateTime sessionExpiresAt;
    private String refreshToken;
    
    public SecurityResponse() {}
    
    public SecurityResponse(String userId, String username, String email, String role, 
                          String status, LocalDateTime lastLoginAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.status = status;
        this.lastLoginAt = lastLoginAt;
        this.success = true;
    }
    
    public SecurityResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public List<User> getUsers() { return users; }
    public void setUsers(List<User> users) { this.users = users; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public LocalDateTime getSessionExpiresAt() { return sessionExpiresAt; }
    public void setSessionExpiresAt(LocalDateTime sessionExpiresAt) { this.sessionExpiresAt = sessionExpiresAt; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    // Factory methods
    public static SecurityResponse fromUser(User user) {
        return new SecurityResponse(
            user.getUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name(),
            user.getStatus().name(),
            user.getLastLoginAt()
        );
    }
    
    public static SecurityResponse success(String message) {
        return new SecurityResponse(true, message);
    }
    
    public static SecurityResponse failure(String message) {
        return new SecurityResponse(false, message);
    }
} 
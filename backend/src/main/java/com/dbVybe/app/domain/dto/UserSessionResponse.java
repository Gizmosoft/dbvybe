package com.dbVybe.app.domain.dto;

import com.dbVybe.app.domain.model.UserSession;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for user session management responses
 */
public class UserSessionResponse {
    private String sessionId;
    private String userId;
    private String username;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String refreshToken;
    private String message;
    private boolean success;
    private List<UserSession> activeSessions;
    private List<UserSession> sessions;
    
    public UserSessionResponse() {}
    
    public UserSessionResponse(String sessionId, String userId, String username, String status, 
                             LocalDateTime createdAt, LocalDateTime expiresAt, String refreshToken) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.refreshToken = refreshToken;
        this.success = true;
    }
    
    public UserSessionResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    
    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public List<UserSession> getActiveSessions() { return activeSessions; }
    public void setActiveSessions(List<UserSession> activeSessions) { this.activeSessions = activeSessions; }
    
    public List<UserSession> getSessions() { return sessions; }
    public void setSessions(List<UserSession> sessions) { this.sessions = sessions; }
    
    // Factory methods
    public static UserSessionResponse fromUserSession(UserSession session) {
        return new UserSessionResponse(
            session.getSessionId(),
            session.getUserId(),
            session.getUsername(),
            session.getStatus().name(),
            session.getCreatedAt(),
            session.getExpiresAt(),
            session.getRefreshToken()
        );
    }
    
    public static UserSessionResponse fromSession(UserSession session) {
        return fromUserSession(session);
    }
    
    public static UserSessionResponse success(String message) {
        return new UserSessionResponse(true, message);
    }
    
    public static UserSessionResponse failure(String message) {
        return new UserSessionResponse(false, message);
    }
} 
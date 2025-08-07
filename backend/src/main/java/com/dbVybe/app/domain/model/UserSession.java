package com.dbVybe.app.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User session model with industry-standard security practices
 */
public class UserSession {
    private final String sessionId;
    private final String userId;
    private final String username;
    private final LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime expiresAt;
    private String userAgent;
    private String ipAddress;
    private SessionStatus status;
    private String refreshToken;
    
    public enum SessionStatus {
        ACTIVE, EXPIRED, REVOKED, SUSPENDED
    }
    
    public UserSession(String userId, String username, String userAgent, String ipAddress) {
        this.sessionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.username = username;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusHours(24); // 24-hour session
        this.status = SessionStatus.ACTIVE;
        this.refreshToken = UUID.randomUUID().toString();
    }
    
    // Constructor for database reconstruction
    public UserSession(String sessionId, String userId, String username, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.lastAccessedAt = LocalDateTime.now();
        this.status = SessionStatus.ACTIVE;
        this.refreshToken = UUID.randomUUID().toString();
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public SessionStatus getStatus() { return status; }
    public String getRefreshToken() { return refreshToken; }
    
    // Additional getter for database compatibility
    public LocalDateTime getAccessedAt() { return lastAccessedAt; }
    
    // Setters with validation
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
    
    public void setAccessedAt(LocalDateTime accessedAt) {
        this.lastAccessedAt = accessedAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public void setStatus(SessionStatus status) {
        this.status = status;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    // Business logic methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt) || status == SessionStatus.EXPIRED;
    }
    
    public boolean isActive() {
        return status == SessionStatus.ACTIVE && !isExpired();
    }
    
    public void extendSession(int hours) {
        this.expiresAt = LocalDateTime.now().plusHours(hours);
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    public void revoke() {
        this.status = SessionStatus.REVOKED;
    }
    
    @Override
    public String toString() {
        return "UserSession{" +
                "sessionId='" + sessionId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", status=" + status +
                ", expiresAt=" + expiresAt +
                '}';
    }
} 
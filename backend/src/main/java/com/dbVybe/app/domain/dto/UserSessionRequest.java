package com.dbVybe.app.domain.dto;

/**
 * DTO for user session management requests
 */
public class UserSessionRequest {
    private String userId;
    private String username;
    private String userAgent;
    private String ipAddress;
    private String sessionId;
    private String refreshToken;
    private int sessionDurationHours;
    
    public UserSessionRequest() {}
    
    public UserSessionRequest(String userId, String username, String userAgent, String ipAddress) {
        this.userId = userId;
        this.username = username;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    public int getSessionDurationHours() { return sessionDurationHours; }
    public void setSessionDurationHours(int sessionDurationHours) { this.sessionDurationHours = sessionDurationHours; }
} 
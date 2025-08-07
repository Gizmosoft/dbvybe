package com.dbVybe.app.domain.dto;

/**
 * DTO for security management requests
 */
public class SecurityRequest {
    private String userId;
    private String username;
    private String email;
    private String password;
    private String currentPassword;
    private String newPassword;
    private String role;
    private String status;
    private String action; // LOGIN, REGISTER, CHANGE_PASSWORD, UPDATE_ROLE, UPDATE_STATUS
    private String userAgent;
    private String ipAddress;
    private String sessionId;
    
    public SecurityRequest() {}
    
    public SecurityRequest(String username, String password) {
        this.username = username;
        this.password = password;
        this.action = "LOGIN";
    }
    
    public SecurityRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.action = "REGISTER";
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
} 
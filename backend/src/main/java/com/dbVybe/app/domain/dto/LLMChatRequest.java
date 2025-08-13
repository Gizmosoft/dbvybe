package com.dbVybe.app.domain.dto;

/**
 * DTO for LLM chat requests from the client
 */
public class LLMChatRequest {
    
    private String message;
    private String sessionId;
    private String connectionId; // Database connection to chat with
    
    // Default constructor
    public LLMChatRequest() {}
    
    // Constructor
    public LLMChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }
    
    // Constructor with connection
    public LLMChatRequest(String message, String sessionId, String connectionId) {
        this.message = message;
        this.sessionId = sessionId;
        this.connectionId = connectionId;
    }
    
    // Getters and setters
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getConnectionId() {
        return connectionId;
    }
    
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    @Override
    public String toString() {
        return "LLMChatRequest{" +
                "message='" + message + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", connectionId='" + connectionId + '\'' +
                '}';
    }
}

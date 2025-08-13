package com.dbVybe.app.domain.dto;

/**
 * DTO for LLM chat requests from the client
 */
public class LLMChatRequest {
    
    private String message;
    private String sessionId;
    
    // Default constructor
    public LLMChatRequest() {}
    
    // Constructor
    public LLMChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
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
    
    @Override
    public String toString() {
        return "LLMChatRequest{" +
                "message='" + message + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}

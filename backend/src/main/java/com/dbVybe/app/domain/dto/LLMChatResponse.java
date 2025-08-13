package com.dbVybe.app.domain.dto;

import java.time.LocalDateTime;

/**
 * DTO for LLM chat responses to the client
 */
public class LLMChatResponse {
    
    private String requestId;
    private String response;
    private boolean success;
    private String error;
    private LocalDateTime timestamp;
    private String sessionId;
    
    // Default constructor
    public LLMChatResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    // Constructor for successful response
    public LLMChatResponse(String requestId, String response, String sessionId) {
        this.requestId = requestId;
        this.response = response;
        this.sessionId = sessionId;
        this.success = true;
        this.error = null;
        this.timestamp = LocalDateTime.now();
    }
    
    // Constructor for error response
    public LLMChatResponse(String requestId, String error, String sessionId, boolean isError) {
        this.requestId = requestId;
        this.response = null;
        this.sessionId = sessionId;
        this.success = false;
        this.error = error;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getResponse() {
        return response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    @Override
    public String toString() {
        return "LLMChatResponse{" +
                "requestId='" + requestId + '\'' +
                ", response='" + response + '\'' +
                ", success=" + success +
                ", error='" + error + '\'' +
                ", timestamp=" + timestamp +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}

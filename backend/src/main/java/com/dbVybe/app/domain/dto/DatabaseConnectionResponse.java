package com.dbVybe.app.domain.dto;

import java.time.LocalDateTime;

/**
 * DTO for database connection response
 */
public class DatabaseConnectionResponse {
    private String connectionId;
    private String status; // SUCCESS, FAILED, CONNECTING
    private String message;
    private LocalDateTime timestamp;
    private DatabaseConnectionRequest request;
    
    public DatabaseConnectionResponse() {}
    
    public DatabaseConnectionResponse(String connectionId, String status, String message) {
        this.connectionId = connectionId;
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public DatabaseConnectionRequest getRequest() { return request; }
    public void setRequest(DatabaseConnectionRequest request) { this.request = request; }
    
    @Override
    public String toString() {
        return "DatabaseConnectionResponse{" +
                "connectionId='" + connectionId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
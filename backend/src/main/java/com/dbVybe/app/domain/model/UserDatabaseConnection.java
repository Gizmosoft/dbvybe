package com.dbVybe.app.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Model representing a user's saved database connection
 */
public class UserDatabaseConnection {
    
    private String connectionId;
    private String userId;
    private String connectionName;
    private String databaseType;
    private String host;
    private int port;
    private String databaseName;
    private String username;
    private String password;
    private Map<String, String> additionalProperties = new HashMap<>();
    private boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public UserDatabaseConnection() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public UserDatabaseConnection(String userId, String connectionName, String databaseType, 
                                String host, int port, String databaseName, 
                                String username, String password) {
        this();
        this.userId = userId;
        this.connectionName = connectionName;
        this.databaseType = databaseType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }
    
    // Getters and Setters
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getConnectionName() { return connectionName; }
    public void setConnectionName(String connectionName) { this.connectionName = connectionName; }
    
    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public Map<String, String> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, String> additionalProperties) { 
        this.additionalProperties = additionalProperties != null ? additionalProperties : new HashMap<>(); 
    }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    
    /**
     * Convert additional properties to JSON string for database storage
     */
    public String getAdditionalPropertiesAsJson() {
        try {
            return objectMapper.writeValueAsString(additionalProperties);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    /**
     * Set additional properties from JSON string from database
     */
    public void setAdditionalPropertiesFromJson(String json) {
        try {
            if (json != null && !json.trim().isEmpty()) {
                this.additionalProperties = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            } else {
                this.additionalProperties = new HashMap<>();
            }
        } catch (JsonProcessingException e) {
            this.additionalProperties = new HashMap<>();
        }
    }
    
    /**
     * Update the updated timestamp
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Mark as used
     */
    public void markAsUsed() {
        this.lastUsedAt = LocalDateTime.now();
        touch();
    }
    
    /**
     * Check if connection details are valid
     */
    public boolean isValid() {
        return connectionName != null && !connectionName.trim().isEmpty() &&
               databaseType != null && !databaseType.trim().isEmpty() &&
               host != null && !host.trim().isEmpty() &&
               port > 0 && port <= 65535 &&
               databaseName != null && !databaseName.trim().isEmpty() &&
               username != null && !username.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return "UserDatabaseConnection{" +
                "connectionId='" + connectionId + '\'' +
                ", userId='" + userId + '\'' +
                ", connectionName='" + connectionName + '\'' +
                ", databaseType='" + databaseType + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", lastUsedAt=" + lastUsedAt +
                '}';
    }
} 
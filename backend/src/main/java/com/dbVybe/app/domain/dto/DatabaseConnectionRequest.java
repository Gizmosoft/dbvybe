package com.dbVybe.app.domain.dto;

import java.util.Map;
import java.util.HashMap;

/**
 * DTO for database connection details
 */
public class DatabaseConnectionRequest {
    private String userId; // User who owns this connection
    private String connectionName; // User-friendly name for the connection
    private String databaseType; // POSTGRESQL, MYSQL, MONGODB
    private String host;
    private int port;
    private String databaseName;
    private String username;
    private String password;
    private Map<String, String> additionalProperties;
    
    public DatabaseConnectionRequest() {
        this.additionalProperties = new HashMap<>();
    }
    
    // Constructor
    public DatabaseConnectionRequest(String databaseType, String host, int port, 
                                  String databaseName, String username, String password) {
        this.databaseType = databaseType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.additionalProperties = new HashMap<>();
    }
    
    // Constructor with user info
    public DatabaseConnectionRequest(String userId, String connectionName, String databaseType, 
                                  String host, int port, String databaseName, 
                                  String username, String password) {
        this(databaseType, host, port, databaseName, username, password);
        this.userId = userId;
        this.connectionName = connectionName;
    }
    
    // Getters and Setters
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
        this.additionalProperties = additionalProperties; 
    }
    
    public void addProperty(String key, String value) {
        this.additionalProperties.put(key, value);
    }
    
    @Override
    public String toString() {
        return "DatabaseConnectionRequest{" +
                "userId='" + userId + '\'' +
                ", connectionName='" + connectionName + '\'' +
                ", databaseType='" + databaseType + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}
package com.dbVybe.app.domain.dto;

import java.util.Map;
import java.util.HashMap;

/**
 * DTO for database connection details
 */
public class DatabaseConnectionRequest {
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
    
    // Getters and Setters
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
                "databaseType='" + databaseType + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", databaseName='" + databaseName + '\'' +
                ", username='" + username + '\'' +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}
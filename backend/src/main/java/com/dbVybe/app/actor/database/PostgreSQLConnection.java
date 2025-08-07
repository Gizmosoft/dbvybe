package com.dbVybe.app.actor.database;

import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * PostgreSQL database connection implementation
 */
public class PostgreSQLConnection implements DatabaseConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLConnection.class);
    
    private final String connectionId;
    private final DatabaseConnectionRequest request;
    private Connection connection;
    
    public PostgreSQLConnection(DatabaseConnectionRequest request, String connectionId) {
        this.request = request;
        this.connectionId = connectionId;
        establishConnection();
    }
    
    @Override
    public String getConnectionId() {
        return connectionId;
    }
    
    @Override
    public DatabaseConnectionRequest getRequest() {
        return request;
    }
    
    @Override
    public void test() {
        try {
            String url = buildConnectionUrl();
            Connection testConnection = DriverManager.getConnection(url, request.getUsername(), request.getPassword());
            testConnection.close();
            logger.info("PostgreSQL connection test successful for: {}", connectionId);
        } catch (Exception e) {
            logger.error("PostgreSQL connection test failed for: {}", connectionId, e);
            throw new RuntimeException("PostgreSQL connection test failed", e);
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("PostgreSQL connection closed: {}", connectionId);
            }
        } catch (Exception e) {
            logger.error("Failed to close PostgreSQL connection: {}", connectionId, e);
        }
    }
    
    @Override
    public boolean isActive() {
        try {
            return connection != null && !connection.isClosed();
        } catch (Exception e) {
            return false;
        }
    }
    
    private void establishConnection() {
        try {
            String url = buildConnectionUrl();
            connection = DriverManager.getConnection(url, request.getUsername(), request.getPassword());
            logger.info("PostgreSQL connection established: {}", connectionId);
        } catch (Exception e) {
            logger.error("Failed to establish PostgreSQL connection: {}", connectionId, e);
            throw new RuntimeException("Failed to establish PostgreSQL connection", e);
        }
    }
    
    private String buildConnectionUrl() {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:postgresql://")
           .append(request.getHost())
           .append(":")
           .append(request.getPort())
           .append("/")
           .append(request.getDatabaseName());
        
        // Add additional properties
        if (!request.getAdditionalProperties().isEmpty()) {
            url.append("?");
            request.getAdditionalProperties().forEach((key, value) -> 
                url.append(key).append("=").append(value).append("&"));
            url.setLength(url.length() - 1); // Remove last &
        }
        
        return url.toString();
    }
}
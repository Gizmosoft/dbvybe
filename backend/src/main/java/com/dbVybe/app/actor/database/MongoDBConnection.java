package com.dbVybe.app.actor.database;

import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * MongoDB database connection implementation
 */
public class MongoDBConnection implements DatabaseConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(MongoDBConnection.class);
    
    private final String connectionId;
    private final DatabaseConnectionRequest request;
    private MongoClient mongoClient;
    private MongoDatabase database;
    
    public MongoDBConnection(DatabaseConnectionRequest request, String connectionId) {
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
            // Test by listing collections
            database.listCollectionNames().first();
            logger.info("MongoDB connection test successful for: {}", connectionId);
        } catch (Exception e) {
            logger.error("MongoDB connection test failed for: {}", connectionId, e);
            throw new RuntimeException("MongoDB connection test failed", e);
        }
    }
    
    @Override
    public void close() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
                logger.info("MongoDB connection closed: {}", connectionId);
            }
        } catch (Exception e) {
            logger.error("Failed to close MongoDB connection: {}", connectionId, e);
        }
    }
    
    @Override
    public boolean isActive() {
        try {
            return mongoClient != null && database != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void establishConnection() {
        try {
            String connectionString = buildConnectionString();
            mongoClient = MongoClients.create(new ConnectionString(connectionString));
            database = mongoClient.getDatabase(request.getDatabaseName());
            logger.info("MongoDB connection established: {}", connectionId);
        } catch (Exception e) {
            logger.error("Failed to establish MongoDB connection: {}", connectionId, e);
            throw new RuntimeException("Failed to establish MongoDB connection", e);
        }
    }
    
    private String buildConnectionString() {
        StringBuilder connectionString = new StringBuilder();
        connectionString.append("mongodb://");
        
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            connectionString.append(request.getUsername());
            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                connectionString.append(":").append(request.getPassword());
            }
            connectionString.append("@");
        }
        
        connectionString.append(request.getHost())
                       .append(":")
                       .append(request.getPort());
        
        // Add additional properties
        if (!request.getAdditionalProperties().isEmpty()) {
            connectionString.append("/?");
            request.getAdditionalProperties().forEach((key, value) -> 
                connectionString.append(key).append("=").append(value).append("&"));
            connectionString.setLength(connectionString.length() - 1); // Remove last &
        }
        
        return connectionString.toString();
    }
}
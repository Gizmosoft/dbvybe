package com.dbVybe.app.actor.database;

import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;

/**
 * Interface for database connections
 * Extensible design for future database support
 */
public interface DatabaseConnection {
    
    /**
     * Get the connection ID
     */
    String getConnectionId();
    
    /**
     * Get the original connection request
     */
    DatabaseConnectionRequest getRequest();
    
    /**
     * Test the connection
     */
    void test();
    
    /**
     * Close the connection
     */
    void close();
    
    /**
     * Check if connection is active
     */
    boolean isActive();
}
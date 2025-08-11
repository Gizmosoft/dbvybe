package com.dbVybe.app.service;

import com.dbVybe.app.domain.dto.DatabaseConnectionRequest;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import com.dbVybe.app.config.DatabaseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user database connections in MySQL database
 */
@Service
public class UserDatabaseConnectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserDatabaseConnectionService.class);
    
    @Autowired
    private DatabaseConfig databaseConfig;
    
    @PostConstruct
    public void initializeDatabase() {
        createUserDatabaseConnectionsTable();
    }
    
    /**
     * Create user_database_connections table if it doesn't exist
     */
    private void createUserDatabaseConnectionsTable() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS user_database_connections (
                connection_id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                connection_name VARCHAR(100) NOT NULL,
                database_type VARCHAR(20) NOT NULL,
                host VARCHAR(255) NOT NULL,
                port INT NOT NULL,
                database_name VARCHAR(100) NOT NULL,
                username VARCHAR(100) NOT NULL,
                password VARCHAR(255) NOT NULL,
                additional_properties TEXT,
                is_active BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                last_used_at TIMESTAMP NULL,
                INDEX idx_user_id (user_id),
                INDEX idx_connection_name (connection_name),
                INDEX idx_database_type (database_type),
                INDEX idx_is_active (is_active),
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
            )
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(createTableSQL)) {
            
            statement.executeUpdate();
            logger.info("User database connections table created/verified successfully");
            
        } catch (SQLException e) {
            logger.error("Failed to create user database connections table", e);
            throw new RuntimeException("Failed to initialize user database connections table", e);
        }
    }
    
    /**
     * Save user database connection
     */
    public boolean saveUserDatabaseConnection(UserDatabaseConnection userDbConnection) {
        String insertSQL = """
            INSERT INTO user_database_connections 
            (connection_id, user_id, connection_name, database_type, host, port, 
             database_name, username, password, additional_properties, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            connection_name = VALUES(connection_name),
            database_type = VALUES(database_type),
            host = VALUES(host),
            port = VALUES(port),
            database_name = VALUES(database_name),
            username = VALUES(username),
            password = VALUES(password),
            additional_properties = VALUES(additional_properties),
            is_active = VALUES(is_active),
            updated_at = VALUES(updated_at)
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(insertSQL)) {
            
            statement.setString(1, userDbConnection.getConnectionId());
            statement.setString(2, userDbConnection.getUserId());
            statement.setString(3, userDbConnection.getConnectionName());
            statement.setString(4, userDbConnection.getDatabaseType());
            statement.setString(5, userDbConnection.getHost());
            statement.setInt(6, userDbConnection.getPort());
            statement.setString(7, userDbConnection.getDatabaseName());
            statement.setString(8, userDbConnection.getUsername());
            statement.setString(9, userDbConnection.getPassword());
            statement.setString(10, userDbConnection.getAdditionalPropertiesAsJson());
            statement.setBoolean(11, userDbConnection.isActive());
            statement.setTimestamp(12, Timestamp.valueOf(userDbConnection.getCreatedAt()));
            statement.setTimestamp(13, Timestamp.valueOf(userDbConnection.getUpdatedAt()));
            
            int rowsAffected = statement.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.info("User database connection saved: {} for user: {}", 
                    userDbConnection.getConnectionId(), userDbConnection.getUserId());
                return true;
            }
            
            return false;
            
        } catch (SQLException e) {
            logger.error("Failed to save user database connection: {}", userDbConnection.getConnectionId(), e);
            return false;
        }
    }
    
    /**
     * Find user database connection by ID and user ID
     */
    public Optional<UserDatabaseConnection> findUserDatabaseConnection(String connectionId, String userId) {
        String selectSQL = """
            SELECT connection_id, user_id, connection_name, database_type, host, port,
                   database_name, username, password, additional_properties, is_active,
                   created_at, updated_at, last_used_at
            FROM user_database_connections 
            WHERE connection_id = ? AND user_id = ? AND is_active = TRUE
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSQL)) {
            
            statement.setString(1, connectionId);
            statement.setString(2, userId);
            
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return Optional.of(mapResultSetToUserDatabaseConnection(resultSet));
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            logger.error("Failed to find user database connection: {} for user: {}", connectionId, userId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Get all database connections for a user
     */
    public List<UserDatabaseConnection> getUserDatabaseConnections(String userId) {
        String selectSQL = """
            SELECT connection_id, user_id, connection_name, database_type, host, port,
                   database_name, username, password, additional_properties, is_active,
                   created_at, updated_at, last_used_at
            FROM user_database_connections 
            WHERE user_id = ? AND is_active = TRUE
            ORDER BY connection_name ASC
            """;
        
        List<UserDatabaseConnection> connections = new ArrayList<>();
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSQL)) {
            
            statement.setString(1, userId);
            
            ResultSet resultSet = statement.executeQuery();
            
            while (resultSet.next()) {
                connections.add(mapResultSetToUserDatabaseConnection(resultSet));
            }
            
            logger.info("Found {} database connections for user: {}", connections.size(), userId);
            
        } catch (SQLException e) {
            logger.error("Failed to get user database connections for user: {}", userId, e);
        }
        
        return connections;
    }
    
    /**
     * Update last used timestamp for a connection
     */
    public boolean updateLastUsed(String connectionId, String userId) {
        String updateSQL = """
            UPDATE user_database_connections 
            SET last_used_at = ? 
            WHERE connection_id = ? AND user_id = ?
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(updateSQL)) {
            
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(2, connectionId);
            statement.setString(3, userId);
            
            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update last used for connection: {} user: {}", connectionId, userId, e);
            return false;
        }
    }
    
    /**
     * Deactivate user database connection (soft delete)
     */
    public boolean deactivateUserDatabaseConnection(String connectionId, String userId) {
        String updateSQL = """
            UPDATE user_database_connections 
            SET is_active = FALSE, updated_at = ? 
            WHERE connection_id = ? AND user_id = ?
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(updateSQL)) {
            
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(2, connectionId);
            statement.setString(3, userId);
            
            int rowsAffected = statement.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.info("User database connection deactivated: {} for user: {}", connectionId, userId);
                return true;
            }
            
            return false;
            
        } catch (SQLException e) {
            logger.error("Failed to deactivate user database connection: {} for user: {}", connectionId, userId, e);
            return false;
        }
    }
    
    /**
     * Hard delete user database connection (permanent delete)
     */
    public boolean deleteUserDatabaseConnection(String connectionId, String userId) {
        String deleteSQL = """
            DELETE FROM user_database_connections
            WHERE connection_id = ? AND user_id = ?
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(deleteSQL)) {
            
            statement.setString(1, connectionId);
            statement.setString(2, userId);
            
            int rowsAffected = statement.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("User database connection deleted: {} for user: {}", connectionId, userId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to delete user database connection: {} for user: {}", connectionId, userId, e);
            return false;
        }
    }
    
    /**
     * Find connection by name for a user
     */
    public Optional<UserDatabaseConnection> findUserDatabaseConnectionByName(String connectionName, String userId) {
        String selectSQL = """
            SELECT connection_id, user_id, connection_name, database_type, host, port,
                   database_name, username, password, additional_properties, is_active,
                   created_at, updated_at, last_used_at
            FROM user_database_connections 
            WHERE connection_name = ? AND user_id = ? AND is_active = TRUE
            """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSQL)) {
            
            statement.setString(1, connectionName);
            statement.setString(2, userId);
            
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                return Optional.of(mapResultSetToUserDatabaseConnection(resultSet));
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            logger.error("Failed to find user database connection by name: {} for user: {}", connectionName, userId, e);
            return Optional.empty();
        }
    }
    
    private UserDatabaseConnection mapResultSetToUserDatabaseConnection(ResultSet resultSet) throws SQLException {
        UserDatabaseConnection connection = new UserDatabaseConnection();
        
        connection.setConnectionId(resultSet.getString("connection_id"));
        connection.setUserId(resultSet.getString("user_id"));
        connection.setConnectionName(resultSet.getString("connection_name"));
        connection.setDatabaseType(resultSet.getString("database_type"));
        connection.setHost(resultSet.getString("host"));
        connection.setPort(resultSet.getInt("port"));
        connection.setDatabaseName(resultSet.getString("database_name"));
        connection.setUsername(resultSet.getString("username"));
        connection.setPassword(resultSet.getString("password"));
        connection.setAdditionalPropertiesFromJson(resultSet.getString("additional_properties"));
        connection.setActive(resultSet.getBoolean("is_active"));
        connection.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        connection.setUpdatedAt(resultSet.getTimestamp("updated_at").toLocalDateTime());
        
        Timestamp lastUsedAt = resultSet.getTimestamp("last_used_at");
        if (lastUsedAt != null) {
            connection.setLastUsedAt(lastUsedAt.toLocalDateTime());
        }
        
        return connection;
    }
    
    private Connection getConnection() throws SQLException {
        String url = databaseConfig.getUrl();
        String username = databaseConfig.getUsername();
        String password = databaseConfig.getPassword();
        
        return DriverManager.getConnection(url, username, password);
    }
} 
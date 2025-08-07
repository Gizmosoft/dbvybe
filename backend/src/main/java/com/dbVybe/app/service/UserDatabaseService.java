package com.dbVybe.app.service;

import com.dbVybe.app.config.DatabaseConfig;
import com.dbVybe.app.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for handling user database operations
 */
@Service
public class UserDatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserDatabaseService.class);
    
    private final DatabaseConfig databaseConfig;
    
    @Autowired
    public UserDatabaseService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        initializeDatabase();
    }
    
    /**
     * Initialize database tables if they don't exist
     */
    private void initializeDatabase() {
        try (Connection connection = getConnection()) {
            createUsersTable(connection);
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
        }
    }
    
    /**
     * Create users table if it doesn't exist
     */
    private void createUsersTable(Connection connection) throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS users (
                user_id VARCHAR(36) PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                email VARCHAR(100) UNIQUE NOT NULL,
                hashed_password VARCHAR(255) NOT NULL,
                salt VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'USER',
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login_at TIMESTAMP NULL,
                login_attempts INT DEFAULT 0,
                locked_until TIMESTAMP NULL,
                INDEX idx_username (username),
                INDEX idx_email (email),
                INDEX idx_role (role),
                INDEX idx_status (status)
            )
        """;
        
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
            logger.info("Users table created/verified successfully");
        }
    }
    
    /**
     * Save user to database
     */
    public boolean saveUser(User user) {
        String sql = """
            INSERT INTO users (user_id, username, email, hashed_password, salt, role, status, created_at, last_login_at, login_attempts, locked_until)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            username = VALUES(username),
            email = VALUES(email),
            hashed_password = VALUES(hashed_password),
            salt = VALUES(salt),
            role = VALUES(role),
            status = VALUES(status),
            last_login_at = VALUES(last_login_at),
            login_attempts = VALUES(login_attempts),
            locked_until = VALUES(locked_until)
        """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, user.getUserId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getEmail());
            statement.setString(4, user.getHashedPassword());
            statement.setString(5, user.getSalt());
            statement.setString(6, user.getRole().name());
            statement.setString(7, user.getStatus().name());
            statement.setTimestamp(8, Timestamp.valueOf(user.getCreatedAt()));
            statement.setTimestamp(9, user.getLastLoginAt() != null ? Timestamp.valueOf(user.getLastLoginAt()) : null);
            statement.setInt(10, user.getLoginAttempts());
            statement.setTimestamp(11, user.getLockedUntil() != null ? Timestamp.valueOf(user.getLockedUntil()) : null);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("User saved to database: {} (rows affected: {})", user.getUserId(), rowsAffected);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to save user to database: {}", user.getUserId(), e);
            return false;
        }
    }
    
    /**
     * Find user by ID
     */
    public Optional<User> findUserById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, userId);
            
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by ID: {}", userId, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Find user by username
     */
    public Optional<User> findUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, username);
            
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by username: {}", username, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Find user by email
     */
    public Optional<User> findUserByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, email);
            
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find user by email: {}", email, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            
            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get all users", e);
        }
        
        return users;
    }
    
    /**
     * Update user role
     */
    public boolean updateUserRole(String userId, User.UserRole role) {
        String sql = "UPDATE users SET role = ? WHERE user_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, role.name());
            statement.setString(2, userId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("User role updated: {} to {}", userId, role);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update user role: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Update user status
     */
    public boolean updateUserStatus(String userId, User.UserStatus status) {
        String sql = "UPDATE users SET status = ? WHERE user_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, status.name());
            statement.setString(2, userId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("User status updated: {} to {}", userId, status);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update user status: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Update login attempts
     */
    public boolean updateLoginAttempts(String userId, int loginAttempts) {
        String sql = "UPDATE users SET login_attempts = ? WHERE user_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setInt(1, loginAttempts);
            statement.setString(2, userId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("Login attempts updated: {} to {}", userId, loginAttempts);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update login attempts: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Update last login time
     */
    public boolean updateLastLogin(String userId, LocalDateTime lastLoginAt) {
        String sql = "UPDATE users SET last_login_at = ?, login_attempts = 0 WHERE user_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setTimestamp(1, Timestamp.valueOf(lastLoginAt));
            statement.setString(2, userId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("Last login updated: {} to {}", userId, lastLoginAt);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update last login: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Delete user
     */
    public boolean deleteUser(String userId) {
        String sql = "DELETE FROM users WHERE user_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, userId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("User deleted: {} (rows affected: {})", userId, rowsAffected);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to delete user: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Map ResultSet to User object
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User(
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("hashed_password"),
            User.UserRole.valueOf(rs.getString("role"))
        );
        
        user.setUserId(rs.getString("user_id"));
        user.setSalt(rs.getString("salt"));
        user.setStatus(User.UserStatus.valueOf(rs.getString("status")));
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        Timestamp lastLoginAt = rs.getTimestamp("last_login_at");
        if (lastLoginAt != null) {
            user.setLastLoginAt(lastLoginAt.toLocalDateTime());
        }
        
        user.setLoginAttempts(rs.getInt("login_attempts"));
        
        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        if (lockedUntil != null) {
            user.setLockedUntil(lockedUntil.toLocalDateTime());
        }
        
        return user;
    }
    
    /**
     * Get database connection
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            databaseConfig.getDatabaseUrl(),
            databaseConfig.getUsername(),
            databaseConfig.getPassword()
        );
    }
} 
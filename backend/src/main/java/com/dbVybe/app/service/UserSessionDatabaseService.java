package com.dbVybe.app.service;

import com.dbVybe.app.config.DatabaseConfig;
import com.dbVybe.app.domain.model.UserSession;
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
 * Service for handling user session database operations
 */
@Service
public class UserSessionDatabaseService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSessionDatabaseService.class);
    
    private final DatabaseConfig databaseConfig;
    
    @Autowired
    public UserSessionDatabaseService(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        initializeDatabase();
    }
    
    /**
     * Initialize database tables if they don't exist
     */
    private void initializeDatabase() {
        try (Connection connection = getConnection()) {
            createUserSessionsTable(connection);
            logger.info("User sessions database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize user sessions database", e);
        }
    }
    
    /**
     * Create user_sessions table if it doesn't exist
     */
    private void createUserSessionsTable(Connection connection) throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS user_sessions (
                session_id VARCHAR(36) PRIMARY KEY,
                user_id VARCHAR(36) NOT NULL,
                username VARCHAR(50) NOT NULL,
                user_agent TEXT,
                ip_address VARCHAR(45),
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                refresh_token VARCHAR(255),
                INDEX idx_user_id (user_id),
                INDEX idx_session_id (session_id),
                INDEX idx_status (status),
                INDEX idx_expires_at (expires_at),
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
            )
        """;
        
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
            logger.info("User sessions table created/verified successfully");
        }
    }
    
    /**
     * Save user session to database
     */
    public boolean saveUserSession(UserSession session) {
        String sql = """
            INSERT INTO user_sessions (session_id, user_id, username, user_agent, ip_address, status, created_at, accessed_at, expires_at, refresh_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            username = VALUES(username),
            user_agent = VALUES(user_agent),
            ip_address = VALUES(ip_address),
            status = VALUES(status),
            accessed_at = VALUES(accessed_at),
            expires_at = VALUES(expires_at),
            refresh_token = VALUES(refresh_token)
        """;
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, session.getSessionId());
            statement.setString(2, session.getUserId());
            statement.setString(3, session.getUsername());
            statement.setString(4, session.getUserAgent());
            statement.setString(5, session.getIpAddress());
            statement.setString(6, session.getStatus().name());
            statement.setTimestamp(7, Timestamp.valueOf(session.getCreatedAt()));
            statement.setTimestamp(8, Timestamp.valueOf(session.getAccessedAt()));
            statement.setTimestamp(9, Timestamp.valueOf(session.getExpiresAt()));
            statement.setString(10, session.getRefreshToken());
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("User session saved to database: {} (rows affected: {})", session.getSessionId(), rowsAffected);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to save user session to database: {}", session.getSessionId(), e);
            return false;
        }
    }
    
    /**
     * Find session by ID
     */
    public Optional<UserSession> findSessionById(String sessionId) {
        String sql = "SELECT * FROM user_sessions WHERE session_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, sessionId);
            
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUserSession(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find session by ID: {}", sessionId, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Find active sessions by user ID
     */
    public List<UserSession> findActiveSessionsByUserId(String userId) {
        List<UserSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM user_sessions WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > NOW() ORDER BY created_at DESC";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, userId);
            
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToUserSession(rs));
                }
            }
            
        } catch (SQLException e) {
            logger.error("Failed to find active sessions for user: {}", userId, e);
        }
        
        return sessions;
    }
    
    /**
     * Update session access time
     */
    public boolean updateSessionAccess(String sessionId, LocalDateTime accessedAt) {
        String sql = "UPDATE user_sessions SET accessed_at = ? WHERE session_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setTimestamp(1, Timestamp.valueOf(accessedAt));
            statement.setString(2, sessionId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("Session access updated: {} to {}", sessionId, accessedAt);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update session access: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Update session expiration
     */
    public boolean updateSessionExpiration(String sessionId, LocalDateTime expiresAt) {
        String sql = "UPDATE user_sessions SET expires_at = ? WHERE session_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setTimestamp(1, Timestamp.valueOf(expiresAt));
            statement.setString(2, sessionId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("Session expiration updated: {} to {}", sessionId, expiresAt);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to update session expiration: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Revoke session (set status to REVOKED)
     */
    public boolean revokeSession(String sessionId) {
        String sql = "UPDATE user_sessions SET status = 'REVOKED' WHERE session_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, sessionId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("Session revoked: {} (rows affected: {})", sessionId, rowsAffected);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to revoke session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Clean up expired sessions
     */
    public int cleanupExpiredSessions() {
        String sql = "UPDATE user_sessions SET status = 'EXPIRED' WHERE expires_at < NOW() AND status = 'ACTIVE'";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            int rowsAffected = statement.executeUpdate();
            logger.info("Cleaned up {} expired sessions", rowsAffected);
            return rowsAffected;
            
        } catch (SQLException e) {
            logger.error("Failed to cleanup expired sessions", e);
            return 0;
        }
    }
    
    /**
     * Get all active sessions
     */
    public List<UserSession> getAllActiveSessions() {
        List<UserSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM user_sessions WHERE status = 'ACTIVE' AND expires_at > NOW() ORDER BY created_at DESC";
        
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            
            while (rs.next()) {
                sessions.add(mapResultSetToUserSession(rs));
            }
            
        } catch (SQLException e) {
            logger.error("Failed to get all active sessions", e);
        }
        
        return sessions;
    }
    
    /**
     * Delete session
     */
    public boolean deleteSession(String sessionId) {
        String sql = "DELETE FROM user_sessions WHERE session_id = ?";
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setString(1, sessionId);
            
            int rowsAffected = statement.executeUpdate();
            logger.debug("Session deleted: {} (rows affected: {})", sessionId, rowsAffected);
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            logger.error("Failed to delete session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Map ResultSet to UserSession object
     */
    private UserSession mapResultSetToUserSession(ResultSet rs) throws SQLException {
        UserSession session = new UserSession(
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("expires_at").toLocalDateTime()
        );
        
        session.setUserAgent(rs.getString("user_agent"));
        session.setIpAddress(rs.getString("ip_address"));
        session.setStatus(UserSession.SessionStatus.valueOf(rs.getString("status")));
        session.setAccessedAt(rs.getTimestamp("accessed_at").toLocalDateTime());
        session.setRefreshToken(rs.getString("refresh_token"));
        
        return session;
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
package com.dbVybe.app.service;

import com.dbVybe.app.domain.dto.UserSessionRequest;
import com.dbVybe.app.domain.dto.UserSessionResponse;
import com.dbVybe.app.domain.model.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for handling user session operations
 */
@Service
public class UserSessionService {
    
    private final UserSessionDatabaseService userSessionDatabaseService;
    private final UserDatabaseService userDatabaseService;
    
    @Autowired
    public UserSessionService(UserSessionDatabaseService userSessionDatabaseService, 
                           UserDatabaseService userDatabaseService) {
        this.userSessionDatabaseService = userSessionDatabaseService;
        this.userDatabaseService = userDatabaseService;
    }
    
    /**
     * Create a new user session
     */
    public UserSessionResponse createSession(UserSessionRequest request) {
        try {
            String userId = request.getUserId();
            String username = request.getUsername();
            String userAgent = request.getUserAgent();
            String ipAddress = request.getIpAddress();
            
            // Validate input
            if (userId == null || username == null) {
                return UserSessionResponse.failure("User ID and username are required");
            }
            
            // Verify user exists
            var userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                return UserSessionResponse.failure("User not found");
            }
            
            var user = userOpt.get();
            if (!user.isActive()) {
                return UserSessionResponse.failure("User account is not active");
            }
            
            // Create new session
            UserSession session = new UserSession(userId, username, userAgent, ipAddress);
            
            // Set custom expiration if provided
            if (request.getSessionDurationHours() > 0) {
                session.setExpiresAt(LocalDateTime.now().plusHours(request.getSessionDurationHours()));
            }
            
            // Save session to database
            boolean saved = userSessionDatabaseService.saveUserSession(session);
            if (!saved) {
                return UserSessionResponse.failure("Failed to create session");
            }
            
            return UserSessionResponse.fromSession(session);
            
        } catch (Exception e) {
            return UserSessionResponse.failure("Failed to create session: " + e.getMessage());
        }
    }
    
    /**
     * Validate a session
     */
    public UserSessionResponse validateSession(UserSessionRequest request) {
        try {
            String sessionId = request.getSessionId();
            
            // Validate input
            if (sessionId == null) {
                return UserSessionResponse.failure("Session ID is required");
            }
            
            // Find session in database
            Optional<UserSession> sessionOpt = userSessionDatabaseService.findSessionById(sessionId);
            if (sessionOpt.isEmpty()) {
                return UserSessionResponse.failure("Session not found");
            }
            
            UserSession session = sessionOpt.get();
            
            // Check if session is active
            if (!session.isActive()) {
                return UserSessionResponse.failure("Session is not active");
            }
            
            // Check if session is expired
            if (session.isExpired()) {
                return UserSessionResponse.failure("Session has expired");
            }
            
            // Update last accessed time
            session.setLastAccessedAt(LocalDateTime.now());
            userSessionDatabaseService.updateSessionAccess(sessionId, LocalDateTime.now());
            
            return UserSessionResponse.fromSession(session);
            
        } catch (Exception e) {
            return UserSessionResponse.failure("Failed to validate session: " + e.getMessage());
        }
    }
    
    /**
     * Extend a session
     */
    public UserSessionResponse extendSession(UserSessionRequest request) {
        try {
            String sessionId = request.getSessionId();
            int durationHours = request.getSessionDurationHours();
            
            // Validate input
            if (sessionId == null) {
                return UserSessionResponse.failure("Session ID is required");
            }
            
            if (durationHours <= 0) {
                return UserSessionResponse.failure("Session duration must be greater than 0 hours");
            }
            
            // Find session in database
            Optional<UserSession> sessionOpt = userSessionDatabaseService.findSessionById(sessionId);
            if (sessionOpt.isEmpty()) {
                return UserSessionResponse.failure("Session not found");
            }
            
            UserSession session = sessionOpt.get();
            
            // Check if session is active
            if (!session.isActive()) {
                return UserSessionResponse.failure("Session is not active");
            }
            
            // Extend session
            LocalDateTime newExpiresAt = LocalDateTime.now().plusHours(durationHours);
            session.setExpiresAt(newExpiresAt);
            session.setLastAccessedAt(LocalDateTime.now());
            
            // Update session in database
            boolean updated = userSessionDatabaseService.updateSessionExpiration(sessionId, newExpiresAt);
            if (!updated) {
                return UserSessionResponse.failure("Failed to extend session");
            }
            
            return UserSessionResponse.fromSession(session);
            
        } catch (Exception e) {
            return UserSessionResponse.failure("Failed to extend session: " + e.getMessage());
        }
    }
    
    /**
     * Revoke a session
     */
    public UserSessionResponse revokeSession(String sessionId) {
        try {
            // Validate input
            if (sessionId == null) {
                return UserSessionResponse.failure("Session ID is required");
            }
            
            // Find session in database
            Optional<UserSession> sessionOpt = userSessionDatabaseService.findSessionById(sessionId);
            if (sessionOpt.isEmpty()) {
                return UserSessionResponse.failure("Session not found");
            }
            
            UserSession session = sessionOpt.get();
            
            // Check if session is already revoked
            if (session.getStatus() == UserSession.SessionStatus.REVOKED) {
                return UserSessionResponse.failure("Session is already revoked");
            }
            
            // Revoke session
            boolean revoked = userSessionDatabaseService.revokeSession(sessionId);
            if (!revoked) {
                return UserSessionResponse.failure("Failed to revoke session");
            }
            
            return UserSessionResponse.success("Session revoked successfully");
            
        } catch (Exception e) {
            return UserSessionResponse.failure("Failed to revoke session: " + e.getMessage());
        }
    }
    
    /**
     * Get user sessions
     */
    public UserSessionResponse getUserSessions(String userId) {
        try {
            // Validate input
            if (userId == null) {
                return UserSessionResponse.failure("User ID is required");
            }
            
            // Verify user exists
            var userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                return UserSessionResponse.failure("User not found");
            }
            
            // Get active sessions for user
            List<UserSession> sessions = userSessionDatabaseService.findActiveSessionsByUserId(userId);
            
            UserSessionResponse response = new UserSessionResponse();
            response.setSessions(sessions);
            response.setSuccess(true);
            response.setMessage("Found " + sessions.size() + " active sessions");
            
            return response;
            
        } catch (Exception e) {
            return UserSessionResponse.failure("Failed to get user sessions: " + e.getMessage());
        }
    }
    
    /**
     * Clean up expired sessions
     */
    public UserSessionResponse cleanupExpiredSessions() {
        try {
            int cleanedCount = userSessionDatabaseService.cleanupExpiredSessions();
            return UserSessionResponse.success("Cleaned up " + cleanedCount + " expired sessions");
            
        } catch (Exception e) {
            return UserSessionResponse.failure("Failed to cleanup expired sessions: " + e.getMessage());
        }
    }
} 
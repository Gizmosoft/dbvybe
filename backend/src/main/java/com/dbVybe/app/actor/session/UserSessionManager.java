package com.dbVybe.app.actor.session;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.dto.UserSessionRequest;
import com.dbVybe.app.domain.dto.UserSessionResponse;
import com.dbVybe.app.domain.model.UserSession;
import com.dbVybe.app.service.UserSessionDatabaseService;
import com.dbVybe.app.service.ActorServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Actor responsible for managing user sessions with industry-standard security practices
 * Includes session creation, validation, extension, and cleanup
 */
public class UserSessionManager extends AbstractBehavior<UserSessionManager.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSessionManager.class);
    
    // Command interface
    public interface Command {}
    
    // Commands
    public static class CreateSession implements Command {
        private final UserSessionRequest request;
        private final akka.actor.typed.ActorRef<SessionResponse> replyTo;
        
        public CreateSession(UserSessionRequest request, akka.actor.typed.ActorRef<SessionResponse> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public UserSessionRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<SessionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class ValidateSession implements Command {
        private final String sessionId;
        private final akka.actor.typed.ActorRef<SessionResponse> replyTo;
        
        public ValidateSession(String sessionId, akka.actor.typed.ActorRef<SessionResponse> replyTo) {
            this.sessionId = sessionId;
            this.replyTo = replyTo;
        }
        
        public String getSessionId() { return sessionId; }
        public akka.actor.typed.ActorRef<SessionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class ExtendSession implements Command {
        private final String sessionId;
        private final int hours;
        private final akka.actor.typed.ActorRef<SessionResponse> replyTo;
        
        public ExtendSession(String sessionId, int hours, akka.actor.typed.ActorRef<SessionResponse> replyTo) {
            this.sessionId = sessionId;
            this.hours = hours;
            this.replyTo = replyTo;
        }
        
        public String getSessionId() { return sessionId; }
        public int getHours() { return hours; }
        public akka.actor.typed.ActorRef<SessionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class RevokeSession implements Command {
        private final String sessionId;
        private final akka.actor.typed.ActorRef<SessionResponse> replyTo;
        
        public RevokeSession(String sessionId, akka.actor.typed.ActorRef<SessionResponse> replyTo) {
            this.sessionId = sessionId;
            this.replyTo = replyTo;
        }
        
        public String getSessionId() { return sessionId; }
        public akka.actor.typed.ActorRef<SessionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class GetUserSessions implements Command {
        private final String userId;
        private final akka.actor.typed.ActorRef<SessionResponse> replyTo;
        
        public GetUserSessions(String userId, akka.actor.typed.ActorRef<SessionResponse> replyTo) {
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getUserId() { return userId; }
        public akka.actor.typed.ActorRef<SessionResponse> getReplyTo() { return replyTo; }
    }
    
    public static class CleanupExpiredSessions implements Command {
        public CleanupExpiredSessions() {}
    }
    
    // Response wrapper
    public static class SessionResponse {
        private final UserSessionResponse response;
        
        public SessionResponse(UserSessionResponse response) {
            this.response = response;
        }
        
        public UserSessionResponse getResponse() { return response; }
    }
    
    public static Behavior<Command> create() {
        return Behaviors.setup(UserSessionManager::new);
    }
    
    private UserSessionManager(ActorContext<Command> context) {
        super(context);
        logger.info("UserSessionManager created with database service");
        
        // Schedule periodic cleanup of expired sessions
        scheduleCleanup(context);
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(CreateSession.class, this::onCreateSession)
            .onMessage(ValidateSession.class, this::onValidateSession)
            .onMessage(ExtendSession.class, this::onExtendSession)
            .onMessage(RevokeSession.class, this::onRevokeSession)
            .onMessage(GetUserSessions.class, this::onGetUserSessions)
            .onMessage(CleanupExpiredSessions.class, this::onCleanupExpiredSessions)
            .build();
    }
    
    private UserSessionDatabaseService getUserSessionDatabaseService() {
        return ActorServiceLocator.getUserSessionDatabaseService();
    }
    
    private Behavior<Command> onCreateSession(CreateSession command) {
        try {
            UserSessionRequest request = command.getRequest();
            
            // Validate request
            if (request.getUserId() == null || request.getUsername() == null) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("User ID and username are required")
                ));
                return Behaviors.same();
            }
            
            // Create new session
            UserSession session = new UserSession(
                request.getUserId(),
                request.getUsername(),
                request.getUserAgent(),
                request.getIpAddress()
            );
            
            // ✅ SAVE TO DATABASE
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService == null) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session database service not available")
                ));
                return Behaviors.same();
            }
            
            boolean saved = sessionService.saveUserSession(session);
            if (!saved) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Failed to save session to database")
                ));
                return Behaviors.same();
            }
            
            logger.info("Created session: {} for user: {}", session.getSessionId(), request.getUsername());
            
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.fromUserSession(session)
            ));
            
        } catch (Exception e) {
            logger.error("Failed to create session", e);
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.failure("Failed to create session: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onValidateSession(ValidateSession command) {
        try {
            String sessionId = command.getSessionId();
            
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService == null) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session database service not available")
                ));
                return Behaviors.same();
            }
            
            // ✅ GET FROM DATABASE
            Optional<UserSession> sessionOpt = sessionService.findSessionById(sessionId);
            if (sessionOpt.isEmpty()) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session not found")
                ));
                return Behaviors.same();
            }
            
            UserSession session = sessionOpt.get();
            
            if (session.isExpired()) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session expired")
                ));
                return Behaviors.same();
            }
            
            // Update last accessed time
            session.setLastAccessedAt(LocalDateTime.now());
            sessionService.updateSessionAccess(sessionId, LocalDateTime.now());
            
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.fromUserSession(session)
            ));
            
        } catch (Exception e) {
            logger.error("Failed to validate session", e);
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.failure("Failed to validate session: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onExtendSession(ExtendSession command) {
        try {
            String sessionId = command.getSessionId();
            int hours = command.getHours();
            
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService == null) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session database service not available")
                ));
                return Behaviors.same();
            }
            
            // ✅ GET FROM DATABASE
            Optional<UserSession> sessionOpt = sessionService.findSessionById(sessionId);
            if (sessionOpt.isEmpty()) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session not found")
                ));
                return Behaviors.same();
            }
            
            UserSession session = sessionOpt.get();
            
            if (session.isExpired()) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session expired")
                ));
                return Behaviors.same();
            }
            
            // Extend session
            LocalDateTime newExpiresAt = LocalDateTime.now().plusHours(hours);
            session.setExpiresAt(newExpiresAt);
            session.setLastAccessedAt(LocalDateTime.now());
            
            // ✅ UPDATE DATABASE
            boolean updated = sessionService.updateSessionExpiration(sessionId, newExpiresAt);
            if (!updated) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Failed to extend session")
                ));
                return Behaviors.same();
            }
            
            logger.info("Extended session: {} by {} hours", sessionId, hours);
            
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.fromUserSession(session)
            ));
            
        } catch (Exception e) {
            logger.error("Failed to extend session", e);
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.failure("Failed to extend session: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onRevokeSession(RevokeSession command) {
        try {
            String sessionId = command.getSessionId();
            
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService == null) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session database service not available")
                ));
                return Behaviors.same();
            }
            
            // ✅ REVOKE IN DATABASE
            boolean revoked = sessionService.revokeSession(sessionId);
            if (!revoked) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session not found or already revoked")
                ));
                return Behaviors.same();
            }
            
            logger.info("Revoked session: {}", sessionId);
            
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.success("Session revoked successfully")
            ));
            
        } catch (Exception e) {
            logger.error("Failed to revoke session", e);
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.failure("Failed to revoke session: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetUserSessions(GetUserSessions command) {
        try {
            String userId = command.getUserId();
            
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService == null) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("Session database service not available")
                ));
                return Behaviors.same();
            }
            
            // ✅ GET FROM DATABASE
            List<UserSession> sessions = sessionService.findActiveSessionsByUserId(userId);
            
            if (sessions.isEmpty()) {
                command.getReplyTo().tell(new SessionResponse(
                    UserSessionResponse.failure("No active sessions found for user")
                ));
                return Behaviors.same();
            }
            
            UserSessionResponse response = new UserSessionResponse();
            response.setSessions(sessions);
            response.setSuccess(true);
            response.setMessage("Found " + sessions.size() + " active sessions");
            
            command.getReplyTo().tell(new SessionResponse(response));
            
        } catch (Exception e) {
            logger.error("Failed to get user sessions", e);
            command.getReplyTo().tell(new SessionResponse(
                UserSessionResponse.failure("Failed to get user sessions: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onCleanupExpiredSessions(CleanupExpiredSessions command) {
        try {
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService != null) {
                // ✅ CLEANUP IN DATABASE
                int cleanedCount = sessionService.cleanupExpiredSessions();
                logger.info("Cleaned up {} expired sessions", cleanedCount);
            }
            
        } catch (Exception e) {
            logger.error("Failed to cleanup expired sessions", e);
        }
        
        return Behaviors.same();
    }
    
    private void scheduleCleanup(ActorContext<Command> context) {
        // Schedule cleanup every 5 minutes
        context.getSystem().scheduler().scheduleAtFixedRate(
            java.time.Duration.ofMinutes(5),
            java.time.Duration.ofMinutes(5),
            () -> context.getSelf().tell(new CleanupExpiredSessions()),
            context.getExecutionContext()
        );
    }
} 
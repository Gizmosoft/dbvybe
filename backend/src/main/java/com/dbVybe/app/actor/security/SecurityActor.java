package com.dbVybe.app.actor.security;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.dto.SecurityRequest;
import com.dbVybe.app.domain.dto.SecurityResponse;
import com.dbVybe.app.domain.model.User;
import com.dbVybe.app.domain.model.UserSession;
import com.dbVybe.app.service.UserDatabaseService;
import com.dbVybe.app.service.UserSessionDatabaseService;
import com.dbVybe.app.service.ActorServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Actor responsible for security management with industry-standard practices
 * Includes user authentication, authorization, password management, and access control
 */
public class SecurityActor extends AbstractBehavior<SecurityActor.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityActor.class);
    
    // Command interface
    public interface Command {}
    
    // Commands
    public static class AuthenticateUser implements Command {
        private final SecurityRequest request;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public AuthenticateUser(SecurityRequest request, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public SecurityRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class RegisterUser implements Command {
        private final SecurityRequest request;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public RegisterUser(SecurityRequest request, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public SecurityRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class ChangePassword implements Command {
        private final SecurityRequest request;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public ChangePassword(SecurityRequest request, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public SecurityRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class UpdateUserRole implements Command {
        private final SecurityRequest request;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public UpdateUserRole(SecurityRequest request, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public SecurityRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class UpdateUserStatus implements Command {
        private final SecurityRequest request;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public UpdateUserStatus(SecurityRequest request, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.request = request;
            this.replyTo = replyTo;
        }
        
        public SecurityRequest getRequest() { return request; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class GetUser implements Command {
        private final String userId;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public GetUser(String userId, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getUserId() { return userId; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class GetAllUsers implements Command {
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public GetAllUsers(akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.replyTo = replyTo;
        }
        
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class ValidateAccess implements Command {
        private final String userId;
        private final String requiredRole;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public ValidateAccess(String userId, String requiredRole, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.userId = userId;
            this.requiredRole = requiredRole;
            this.replyTo = replyTo;
        }
        
        public String getUserId() { return userId; }
        public String getRequiredRole() { return requiredRole; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    public static class LogoutUser implements Command {
        private final String sessionId;
        private final akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo;
        
        public LogoutUser(String sessionId, akka.actor.typed.ActorRef<SecurityResponseWrapper> replyTo) {
            this.sessionId = sessionId;
            this.replyTo = replyTo;
        }
        
        public String getSessionId() { return sessionId; }
        public akka.actor.typed.ActorRef<SecurityResponseWrapper> getReplyTo() { return replyTo; }
    }
    
    // Response wrapper
    public static class SecurityResponseWrapper {
        private final SecurityResponse response;
        
        public SecurityResponseWrapper(SecurityResponse response) {
            this.response = response;
        }
        
        public SecurityResponse getResponse() { return response; }
    }
    
    // Actor state
    private final SecureRandom secureRandom = new SecureRandom();
    
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new SecurityActor(context));
    }
    
    private SecurityActor(ActorContext<Command> context) {
        super(context);
        logger.info("SecurityActor created with database service");
        
        // Initialize with admin user
        initializeAdminUser();
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AuthenticateUser.class, this::onAuthenticateUser)
            .onMessage(RegisterUser.class, this::onRegisterUser)
            .onMessage(ChangePassword.class, this::onChangePassword)
            .onMessage(UpdateUserRole.class, this::onUpdateUserRole)
            .onMessage(UpdateUserStatus.class, this::onUpdateUserStatus)
            .onMessage(GetUser.class, this::onGetUser)
            .onMessage(GetAllUsers.class, this::onGetAllUsers)
            .onMessage(ValidateAccess.class, this::onValidateAccess)
            .onMessage(LogoutUser.class, this::onLogoutUser)
            .build();
    }
    
    private UserDatabaseService getUserDatabaseService() {
        return ActorServiceLocator.getUserDatabaseService();
    }
    
    private UserSessionDatabaseService getUserSessionDatabaseService() {
        return ActorServiceLocator.getUserSessionDatabaseService();
    }
    
    private Behavior<Command> onAuthenticateUser(AuthenticateUser command) {
        try {
            SecurityRequest request = command.getRequest();
            String username = request.getUsername();
            String password = request.getPassword();
            
            // Validate input
            if (username == null || password == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Username and password are required")
                ));
                return Behaviors.same();
            }
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            // Find user by username from database
            Optional<User> userOpt = userDatabaseService.findUserByUsername(username);
            if (userOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Invalid credentials")
                ));
                return Behaviors.same();
            }
            
            User user = userOpt.get();
            
            // Check if user is locked
            if (user.isLocked()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Account is locked. Try again later.")
                ));
                return Behaviors.same();
            }
            
            // Check if user is active
            if (!user.isActive()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Account is not active")
                ));
                return Behaviors.same();
            }
            
            // Verify password
            if (!verifyPassword(password, user.getHashedPassword(), user.getSalt())) {
                user.incrementLoginAttempts();
                userDatabaseService.saveUser(user);
                
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Invalid credentials")
                ));
                return Behaviors.same();
            }
            
            // Reset login attempts on successful login
            user.resetLoginAttempts();
            user.setLastLoginAt(LocalDateTime.now());
            userDatabaseService.saveUser(user);
            
            // ✅ CREATE SESSION AUTOMATICALLY
            UserSession session = createUserSession(user, request.getUserAgent(), request.getIpAddress());
            
            // Return response with session info
            SecurityResponse response = SecurityResponse.fromUser(user);
            response.setSessionId(session.getSessionId());
            response.setSessionExpiresAt(session.getExpiresAt());
            response.setRefreshToken(session.getRefreshToken());
            
            logger.info("User authenticated successfully: {} with session: {}", username, session.getSessionId());
            
            command.getReplyTo().tell(new SecurityResponseWrapper(response));
            
        } catch (Exception e) {
            logger.error("Failed to authenticate user", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Authentication failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onRegisterUser(RegisterUser command) {
        try {
            SecurityRequest request = command.getRequest();
            String username = request.getUsername();
            String email = request.getEmail();
            String password = request.getPassword();
            
            // Validate input
            if (username == null || email == null || password == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Username, email, and password are required")
                ));
                return Behaviors.same();
            }
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            // Check if username already exists
            if (userDatabaseService.findUserByUsername(username).isPresent()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Username already exists")
                ));
                return Behaviors.same();
            }
            
            // Check if email already exists
            if (userDatabaseService.findUserByEmail(email).isPresent()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Email already exists")
                ));
                return Behaviors.same();
            }
            
            // Validate password strength
            if (!isPasswordStrong(password)) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Password must be at least 8 characters long and contain uppercase, lowercase, number, and special character")
                ));
                return Behaviors.same();
            }
            
            // Create new user
            String salt = generateSalt();
            String hashedPassword = hashPassword(password, salt);
            User user = new User(username, email, hashedPassword, User.UserRole.USER);
            user.setSalt(salt);
            
            // Store user in database
            boolean saved = userDatabaseService.saveUser(user);
            if (!saved) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Failed to save user to database")
                ));
                return Behaviors.same();
            }
            
            logger.info("User registered successfully: {}", username);
            
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.fromUser(user)
            ));
            
        } catch (Exception e) {
            logger.error("Failed to register user", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Registration failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onChangePassword(ChangePassword command) {
        try {
            SecurityRequest request = command.getRequest();
            String userId = request.getUserId();
            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            
            // Validate input
            if (userId == null || currentPassword == null || newPassword == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User ID, current password, and new password are required")
                ));
                return Behaviors.same();
            }
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            // Find user from database
            Optional<User> userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User not found")
                ));
                return Behaviors.same();
            }
            
            User user = userOpt.get();
            
            // Verify current password
            if (!verifyPassword(currentPassword, user.getHashedPassword(), user.getSalt())) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Current password is incorrect")
                ));
                return Behaviors.same();
            }
            
            // Validate new password strength
            if (!isPasswordStrong(newPassword)) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("New password must be at least 8 characters long and contain uppercase, lowercase, number, and special character")
                ));
                return Behaviors.same();
            }
            
            // Create new user with updated password
            String newSalt = generateSalt();
            String newHashedPassword = hashPassword(newPassword, newSalt);
            User updatedUser = new User(user.getUserId(), user.getUsername(), user.getEmail(), newHashedPassword, user.getRole(), user.getCreatedAt());
            updatedUser.setSalt(newSalt);
            updatedUser.setStatus(user.getStatus());
            updatedUser.setLastLoginAt(user.getLastLoginAt());
            updatedUser.setLoginAttempts(user.getLoginAttempts());
            updatedUser.setLockedUntil(user.getLockedUntil());
            
            // Save updated user to database
            boolean saved = userDatabaseService.saveUser(updatedUser);
            if (!saved) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Failed to update password in database")
                ));
                return Behaviors.same();
            }
            
            logger.info("Password changed successfully for user: {}", userId);
            
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.success("Password changed successfully")
            ));
            
        } catch (Exception e) {
            logger.error("Failed to change password", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Password change failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onUpdateUserRole(UpdateUserRole command) {
        try {
            SecurityRequest request = command.getRequest();
            String userId = request.getUserId();
            String role = request.getRole();
            
            // Validate input
            if (userId == null || role == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User ID and role are required")
                ));
                return Behaviors.same();
            }
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            // Find user from database
            Optional<User> userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User not found")
                ));
                return Behaviors.same();
            }
            
            User user = userOpt.get();
            
            // Update role
            try {
                User.UserRole newRole = User.UserRole.valueOf(role.toUpperCase());
                user.setRole(newRole);
                
                // Save to database
                boolean saved = userDatabaseService.saveUser(user);
                if (!saved) {
                    command.getReplyTo().tell(new SecurityResponseWrapper(
                        SecurityResponse.failure("Failed to update user role in database")
                    ));
                    return Behaviors.same();
                }
                
                logger.info("User role updated for user: {} to role: {}", userId, role);
                
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.fromUser(user)
                ));
                
            } catch (IllegalArgumentException e) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Invalid role: " + role)
                ));
            }
            
        } catch (Exception e) {
            logger.error("Failed to update user role", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Role update failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onUpdateUserStatus(UpdateUserStatus command) {
        try {
            SecurityRequest request = command.getRequest();
            String userId = request.getUserId();
            String status = request.getStatus();
            
            // Validate input
            if (userId == null || status == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User ID and status are required")
                ));
                return Behaviors.same();
            }
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            // Find user from database
            Optional<User> userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User not found")
                ));
                return Behaviors.same();
            }
            
            User user = userOpt.get();
            
            // Update status
            try {
                User.UserStatus newStatus = User.UserStatus.valueOf(status.toUpperCase());
                user.setStatus(newStatus);
                
                // Save to database
                boolean saved = userDatabaseService.saveUser(user);
                if (!saved) {
                    command.getReplyTo().tell(new SecurityResponseWrapper(
                        SecurityResponse.failure("Failed to update user status in database")
                    ));
                    return Behaviors.same();
                }
                
                logger.info("User status updated for user: {} to status: {}", userId, status);
                
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.fromUser(user)
                ));
                
            } catch (IllegalArgumentException e) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Invalid status: " + status)
                ));
            }
            
        } catch (Exception e) {
            logger.error("Failed to update user status", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Status update failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetUser(GetUser command) {
        try {
            String userId = command.getUserId();
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            Optional<User> userOpt = userDatabaseService.findUserById(userId);
            
            if (userOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User not found")
                ));
                return Behaviors.same();
            }
            
            User user = userOpt.get();
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.fromUser(user)
            ));
            
        } catch (Exception e) {
            logger.error("Failed to get user", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Failed to get user: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetAllUsers(GetAllUsers command) {
        try {
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            List<User> userList = userDatabaseService.getAllUsers();
            
            SecurityResponse response = new SecurityResponse();
            response.setUsers(userList);
            response.setSuccess(true);
            response.setMessage("Found " + userList.size() + " users");
            
            command.getReplyTo().tell(new SecurityResponseWrapper(response));
            
        } catch (Exception e) {
            logger.error("Failed to get all users", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Failed to get users: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onValidateAccess(ValidateAccess command) {
        try {
            String userId = command.getUserId();
            String requiredRole = command.getRequiredRole();
            
            UserDatabaseService userDatabaseService = getUserDatabaseService();
            if (userDatabaseService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Database service not available")
                ));
                return Behaviors.same();
            }
            
            Optional<User> userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User not found")
                ));
                return Behaviors.same();
            }
            
            User user = userOpt.get();
            
            if (!user.isActive()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("User account is not active")
                ));
                return Behaviors.same();
            }
            
            try {
                User.UserRole role = User.UserRole.valueOf(requiredRole.toUpperCase());
                boolean hasAccess = user.hasRole(role);
                
                if (hasAccess) {
                    command.getReplyTo().tell(new SecurityResponseWrapper(
                        SecurityResponse.success("Access granted")
                    ));
                } else {
                    command.getReplyTo().tell(new SecurityResponseWrapper(
                        SecurityResponse.failure("Access denied. Required role: " + requiredRole)
                    ));
                }
                
            } catch (IllegalArgumentException e) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Invalid role: " + requiredRole)
                ));
            }
            
        } catch (Exception e) {
            logger.error("Failed to validate access", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Access validation failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onLogoutUser(LogoutUser command) {
        try {
            String sessionId = command.getSessionId();
            
            UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
            if (sessionService == null) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Session database service not available")
                ));
                return Behaviors.same();
            }
            
            Optional<UserSession> sessionOpt = sessionService.findSessionById(sessionId);
            
            if (sessionOpt.isEmpty()) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Session not found")
                ));
                return Behaviors.same();
            }
            
            UserSession session = sessionOpt.get();
            
            // Revoke session
            boolean revoked = sessionService.revokeSession(sessionId);
            
            if (!revoked) {
                command.getReplyTo().tell(new SecurityResponseWrapper(
                    SecurityResponse.failure("Failed to revoke session")
                ));
                return Behaviors.same();
            }
            
            logger.info("User logged out successfully: {}", sessionId);
            
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.success("User logged out successfully")
            ));
            
        } catch (Exception e) {
            logger.error("Failed to log out user", e);
            command.getReplyTo().tell(new SecurityResponseWrapper(
                SecurityResponse.failure("Logout failed: " + e.getMessage())
            ));
        }
        
        return Behaviors.same();
    }
    
    /**
     * Create user session automatically
     */
    private UserSession createUserSession(User user, String userAgent, String ipAddress) {
        UserSession session = new UserSession(
            user.getUserId(), 
            user.getUsername(), 
            userAgent, 
            ipAddress
        );
        
        // Save session to database
        UserSessionDatabaseService sessionService = getUserSessionDatabaseService();
        if (sessionService != null) {
            boolean saved = sessionService.saveUserSession(session);
            if (!saved) {
                logger.error("Failed to save session to database: {}", session.getSessionId());
            } else {
                logger.info("Session saved to database: {}", session.getSessionId());
            }
        } else {
            logger.error("UserSessionDatabaseService not available");
        }
        
        return session;
    }
    
    // Helper methods
    private void initializeAdminUser() {
        UserDatabaseService userDatabaseService = getUserDatabaseService();
        if (userDatabaseService == null) {
            logger.error("Database service not available for admin initialization");
            return;
        }
        
        String adminUsername = "admin";
        String adminEmail = "admin@dbvybe.com";
        String adminPassword = "Admin@123";
        String salt = generateSalt();
        String hashedPassword = hashPassword(adminPassword, salt);
        
        User adminUser = new User(adminUsername, adminEmail, hashedPassword, User.UserRole.ADMIN);
        adminUser.setSalt(salt);
        
        // Check if admin already exists
        if (userDatabaseService.findUserByUsername(adminUsername).isEmpty()) {
            boolean saved = userDatabaseService.saveUser(adminUser);
            if (saved) {
                logger.info("Admin user initialized: {}", adminUsername);
            } else {
                logger.error("Failed to initialize admin user");
            }
        } else {
            logger.info("Admin user already exists");
        }
    }
    
    private String generateSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + salt;
            byte[] hashedBytes = md.digest(saltedPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    private boolean verifyPassword(String password, String hashedPassword, String salt) {
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(hashedPassword);
    }
    
    private boolean isPasswordStrong(String password) {
        if (password.length() < 8) return false;
        
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
        
        return hasUpper && hasLower && hasNumber && hasSpecial;
    }
} 
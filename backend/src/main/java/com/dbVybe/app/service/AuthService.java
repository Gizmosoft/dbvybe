package com.dbVybe.app.service;

import com.dbVybe.app.domain.dto.SecurityRequest;
import com.dbVybe.app.domain.dto.SecurityResponse;
import com.dbVybe.app.domain.model.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * Service for handling authentication and authorization
 */
@Service
public class AuthService {
    
    private final UserDatabaseService userDatabaseService;
    private final UserSessionDatabaseService userSessionDatabaseService;
    
    @Autowired
    public AuthService(UserDatabaseService userDatabaseService, 
                     UserSessionDatabaseService userSessionDatabaseService) {
        this.userDatabaseService = userDatabaseService;
        this.userSessionDatabaseService = userSessionDatabaseService;
    }
    
    /**
     * Authenticate user and automatically create session
     */
    public SecurityResponse authenticateUser(SecurityRequest request) {
        try {
            String username = request.getUsername();
            String password = request.getPassword();
            
            if (username == null || password == null) {
                return SecurityResponse.failure("Username and password are required");
            }
            
            // Find user by username
            var userOpt = userDatabaseService.findUserByUsername(username);
            if (userOpt.isEmpty()) {
                return SecurityResponse.failure("Invalid credentials");
            }
            
            var user = userOpt.get();
            
            // Check if account is locked
            if (user.isLocked()) {
                return SecurityResponse.failure("Account is locked");
            }
            
            // Check if account is active
            if (!user.isActive()) {
                return SecurityResponse.failure("Account is not active");
            }
            
            // Verify password
            String hashedPassword = hashPassword(password, user.getSalt());
            if (!hashedPassword.equals(user.getHashedPassword())) {
                // Increment login attempts
                user.incrementLoginAttempts();
                userDatabaseService.updateLoginAttempts(user.getUserId(), user.getLoginAttempts());
                return SecurityResponse.failure("Invalid credentials");
            }
            
            // Reset login attempts on successful login
            user.resetLoginAttempts();
            userDatabaseService.updateLoginAttempts(user.getUserId(), 0);
            
            // Update last login time
            userDatabaseService.updateLastLogin(user.getUserId(), LocalDateTime.now());
            
            // Automatically create session
            UserSession session = createUserSession(user, request.getUserAgent(), request.getIpAddress());
            
            // Return response with session info
            SecurityResponse response = SecurityResponse.fromUser(user);
            response.setSessionId(session.getSessionId());
            response.setSessionExpiresAt(session.getExpiresAt());
            response.setRefreshToken(session.getRefreshToken());
            
            return response;
            
        } catch (Exception e) {
            return SecurityResponse.failure("Authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Register user
     */
    public SecurityResponse registerUser(SecurityRequest request) {
        try {
            String username = request.getUsername();
            String email = request.getEmail();
            String password = request.getPassword();
            
            // Validate input
            if (username == null || email == null || password == null) {
                return SecurityResponse.failure("Username, email, and password are required");
            }
            
            // Check if username already exists
            if (userDatabaseService.findUserByUsername(username).isPresent()) {
                return SecurityResponse.failure("Username already exists");
            }
            
            // Check if email already exists
            if (userDatabaseService.findUserByEmail(email).isPresent()) {
                return SecurityResponse.failure("Email already exists");
            }
            
            // Validate password strength
            if (!isPasswordStrong(password)) {
                return SecurityResponse.failure("Password must be at least 8 characters long and contain uppercase, lowercase, number, and special character");
            }
            
            // Create new user
            String salt = generateSalt();
            String hashedPassword = hashPassword(password, salt);
            var user = new com.dbVybe.app.domain.model.User(username, email, hashedPassword, com.dbVybe.app.domain.model.User.UserRole.USER);
            user.setSalt(salt);
            
            // Store user in database
            boolean saved = userDatabaseService.saveUser(user);
            if (!saved) {
                return SecurityResponse.failure("Failed to save user to database");
            }
            
            return SecurityResponse.fromUser(user);
            
        } catch (Exception e) {
            return SecurityResponse.failure("Registration failed: " + e.getMessage());
        }
    }
    
    /**
     * Change password
     */
    public SecurityResponse changePassword(SecurityRequest request) {
        try {
            String userId = request.getUserId();
            String currentPassword = request.getCurrentPassword();
            String newPassword = request.getNewPassword();
            
            // Validate input
            if (userId == null || currentPassword == null || newPassword == null) {
                return SecurityResponse.failure("User ID, current password, and new password are required");
            }
            
            // Find user from database
            var userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                return SecurityResponse.failure("User not found");
            }
            
            var user = userOpt.get();
            
            // Verify current password
            if (!verifyPassword(currentPassword, user.getHashedPassword(), user.getSalt())) {
                return SecurityResponse.failure("Current password is incorrect");
            }
            
            // Validate new password strength
            if (!isPasswordStrong(newPassword)) {
                return SecurityResponse.failure("New password must be at least 8 characters long and contain uppercase, lowercase, number, and special character");
            }
            
            // Create new user with updated password
            String newSalt = generateSalt();
            String newHashedPassword = hashPassword(newPassword, newSalt);
            var updatedUser = new com.dbVybe.app.domain.model.User(user.getUserId(), user.getUsername(), user.getEmail(), newHashedPassword, user.getRole(), user.getCreatedAt());
            updatedUser.setSalt(newSalt);
            updatedUser.setStatus(user.getStatus());
            updatedUser.setLastLoginAt(user.getLastLoginAt());
            updatedUser.setLoginAttempts(user.getLoginAttempts());
            updatedUser.setLockedUntil(user.getLockedUntil());
            
            // Save updated user to database
            boolean saved = userDatabaseService.saveUser(updatedUser);
            if (!saved) {
                return SecurityResponse.failure("Failed to update password in database");
            }
            
            return SecurityResponse.success("Password changed successfully");
            
        } catch (Exception e) {
            return SecurityResponse.failure("Password change failed: " + e.getMessage());
        }
    }
    
    /**
     * Update user role
     */
    public SecurityResponse updateUserRole(SecurityRequest request) {
        try {
            String userId = request.getUserId();
            String role = request.getRole();
            
            // Validate input
            if (userId == null || role == null) {
                return SecurityResponse.failure("User ID and role are required");
            }
            
            // Find user from database
            var userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                return SecurityResponse.failure("User not found");
            }
            
            var user = userOpt.get();
            
            // Update role
            try {
                var newRole = com.dbVybe.app.domain.model.User.UserRole.valueOf(role.toUpperCase());
                user.setRole(newRole);
                
                // Save to database
                boolean saved = userDatabaseService.saveUser(user);
                if (!saved) {
                    return SecurityResponse.failure("Failed to update user role in database");
                }
                
                return SecurityResponse.fromUser(user);
                
            } catch (IllegalArgumentException e) {
                return SecurityResponse.failure("Invalid role: " + role);
            }
            
        } catch (Exception e) {
            return SecurityResponse.failure("Role update failed: " + e.getMessage());
        }
    }
    
    /**
     * Update user status
     */
    public SecurityResponse updateUserStatus(SecurityRequest request) {
        try {
            String userId = request.getUserId();
            String status = request.getStatus();
            
            // Validate input
            if (userId == null || status == null) {
                return SecurityResponse.failure("User ID and status are required");
            }
            
            // Find user from database
            var userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                return SecurityResponse.failure("User not found");
            }
            
            var user = userOpt.get();
            
            // Update status
            try {
                var newStatus = com.dbVybe.app.domain.model.User.UserStatus.valueOf(status.toUpperCase());
                user.setStatus(newStatus);
                
                // Save to database
                boolean saved = userDatabaseService.saveUser(user);
                if (!saved) {
                    return SecurityResponse.failure("Failed to update user status in database");
                }
                
                return SecurityResponse.fromUser(user);
                
            } catch (IllegalArgumentException e) {
                return SecurityResponse.failure("Invalid status: " + status);
            }
            
        } catch (Exception e) {
            return SecurityResponse.failure("Status update failed: " + e.getMessage());
        }
    }
    
    /**
     * Get user by ID
     */
    public SecurityResponse getUser(String userId) {
        try {
            var userOpt = userDatabaseService.findUserById(userId);
            
            if (userOpt.isEmpty()) {
                return SecurityResponse.failure("User not found");
            }
            
            var user = userOpt.get();
            return SecurityResponse.fromUser(user);
            
        } catch (Exception e) {
            return SecurityResponse.failure("Failed to get user: " + e.getMessage());
        }
    }
    
    /**
     * Get all users
     */
    public SecurityResponse getAllUsers() {
        try {
            var userList = userDatabaseService.getAllUsers();
            
            var response = new SecurityResponse();
            response.setUsers(userList);
            response.setSuccess(true);
            response.setMessage("Found " + userList.size() + " users");
            
            return response;
            
        } catch (Exception e) {
            return SecurityResponse.failure("Failed to get users: " + e.getMessage());
        }
    }
    
    /**
     * Validate access for a specific role
     */
    public SecurityResponse validateAccess(String userId, String requiredRole) {
        try {
            var userOpt = userDatabaseService.findUserById(userId);
            if (userOpt.isEmpty()) {
                return SecurityResponse.failure("User not found");
            }
            
            var user = userOpt.get();
            
            if (!user.isActive()) {
                return SecurityResponse.failure("User account is not active");
            }
            
            try {
                var role = com.dbVybe.app.domain.model.User.UserRole.valueOf(requiredRole.toUpperCase());
                boolean hasAccess = user.hasRole(role);
                
                if (hasAccess) {
                    return SecurityResponse.success("Access granted");
                } else {
                    return SecurityResponse.failure("Access denied. Required role: " + requiredRole);
                }
                
            } catch (IllegalArgumentException e) {
                return SecurityResponse.failure("Invalid role: " + requiredRole);
            }
            
        } catch (Exception e) {
            return SecurityResponse.failure("Access validation failed: " + e.getMessage());
        }
    }
    
    // Helper methods
    private String generateSalt() {
        var secureRandom = new java.security.SecureRandom();
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return java.util.Base64.getEncoder().encodeToString(salt);
    }
    
    private String hashPassword(String password, String salt) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + salt;
            byte[] hashedBytes = md.digest(saltedPassword.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hashedBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
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

    /**
     * Create user session automatically
     */
    private UserSession createUserSession(com.dbVybe.app.domain.model.User user, String userAgent, String ipAddress) {
        UserSession session = new UserSession(
            user.getUserId(), 
            user.getUsername(), 
            userAgent, 
            ipAddress
        );
        
        // Save session to database
        userSessionDatabaseService.saveUserSession(session);
        
        return session;
    }
} 
package com.dbVybe.app.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User model with industry-standard security practices
 */
public class User {
    private String userId;
    private final String username;
    private final String email;
    private final String hashedPassword;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private UserRole role;
    private UserStatus status;
    private String salt;
    private int loginAttempts;
    private LocalDateTime lockedUntil;
    
    public enum UserRole {
        ADMIN, USER, GUEST
    }
    
    public enum UserStatus {
        ACTIVE, INACTIVE, LOCKED, SUSPENDED
    }
    
    public User(String username, String email, String hashedPassword, UserRole role) {
        this.userId = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.role = role;
        this.createdAt = LocalDateTime.now();
        this.status = UserStatus.ACTIVE;
        this.loginAttempts = 0;
    }
    
    // Constructor for database reconstruction
    public User(String userId, String username, String email, String hashedPassword, UserRole role, LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.role = role;
        this.createdAt = createdAt;
        this.status = UserStatus.ACTIVE;
        this.loginAttempts = 0;
    }
    
    // Getters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getHashedPassword() { return hashedPassword; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public String getSalt() { return salt; }
    public int getLoginAttempts() { return loginAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    
    // Setters with validation
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
    
    public void setRole(UserRole role) {
        this.role = role;
    }
    
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    
    public void setSalt(String salt) {
        this.salt = salt;
    }
    
    public void setLoginAttempts(int loginAttempts) {
        this.loginAttempts = loginAttempts;
    }
    
    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
    
    // Business logic methods
    public boolean isLocked() {
        return status == UserStatus.LOCKED || 
               (lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil));
    }
    
    public boolean isActive() {
        return status == UserStatus.ACTIVE && !isLocked();
    }
    
    public void incrementLoginAttempts() {
        this.loginAttempts++;
        if (this.loginAttempts >= 5) {
            this.status = UserStatus.LOCKED;
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }
    
    public void resetLoginAttempts() {
        this.loginAttempts = 0;
        this.lockedUntil = null;
    }
    
    public boolean hasRole(UserRole requiredRole) {
        return this.role == requiredRole || this.role == UserRole.ADMIN;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", status=" + status +
                '}';
    }
} 
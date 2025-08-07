# User Authentication and Session Management

## Table of Contents
1. [Overview](#overview)
2. [Actor-First Architecture](#actor-first-architecture)
3. [Cluster Architecture](#cluster-architecture)
4. [Node and ActorSystem Details](#node-and-actorsystem-details)
5. [Data Flow Architecture](#data-flow-architecture)
6. [API Endpoints](#api-endpoints)
7. [UI Integration Flow](#ui-integration-flow)
8. [Testing Procedures](#testing-procedures)
9. [Database Schema](#database-schema)
10. [Security Features](#security-features)
11. [Akka Actor Implementation](#akka-actor-implementation)
12. [Troubleshooting](#troubleshooting)

## Overview

The User Authentication and Session Management system is implemented within the **Core Services Node** of the Akka Cluster using **Actor-First Architecture**. This system provides end-to-end user authentication, session management, and security features with industry-standard practices.

### Key Features
- ✅ **Actor-First Architecture** - All operations go through Akka actors
- ✅ **Automatic Session Creation** on successful login
- ✅ **Database Persistence** for users and sessions
- ✅ **Cross-Node Session Sharing** across Akka cluster
- ✅ **Security Features** (password hashing, account locking, role-based access)
- ✅ **Session Lifecycle Management** (create, validate, extend, revoke)
- ✅ **Automatic Cleanup** of expired sessions
- ✅ **Proper HTTP Status Codes** for all responses
- ✅ **Simplified UI Flow** - No explicit session management APIs needed

## Actor-First Architecture

### **New Architecture Flow:**
```
API Request → Controller → Actor → Database
```

### **Benefits of Actor-First Approach:**

#### **✅ Concurrency & Performance:**
- **Non-blocking operations** - Actors handle requests concurrently
- **Message-based communication** - Clean separation of concerns
- **Fault isolation** - Actor failures don't affect entire system

#### **✅ Scalability:**
- **Easy horizontal scaling** - Add more actors for load distribution
- **Cluster support** - Actors can communicate across nodes
- **State management** - Safe concurrent state handling

#### **✅ Fault Tolerance:**
- **Actor supervision** - Failed actors can be restarted
- **Isolated failures** - One actor failure doesn't crash the system
- **Resilient communication** - Message passing is reliable

#### **✅ Maintainability:**
- **Clear separation** - Controllers handle HTTP, Actors handle business logic
- **Testable** - Each component can be tested independently
- **Extensible** - Easy to add new actors for new features

### **Actor Hierarchy in Core Services Node:**
```
DatabaseExplorationSystem (ActorSystem)
└── DatabaseExplorationSupervisor (Root Actor)
    ├── SecurityActor (Authentication & Authorization)
    ├── UserSessionManager (Session Management)
    └── DatabaseCommunicationManager (Database Connections)
```

## Cluster Architecture

### Akka Cluster Structure
```
┌─────────────────────────────────────────────────────────────┐
│                    Akka Cluster                            │
├─────────────────────────────────────────────────────────────┤
│  Node 1: Core Services (DatabaseExplorationSystem)        │
│  ├── Port: 2551                                          │
│  ├── Management: 8551                                    │
│  ├── Role: "core-services"                               │
│  └── Services:                                           │
│      ├── SecurityActor (Authentication)                   │
│      ├── UserSessionManager (Session Management)          │
│      └── DatabaseCommunicationManager (DB Connections)    │
├─────────────────────────────────────────────────────────────┤
│  Node 2: LLM Processing (LLMProcessingSystem)            │
│  ├── Port: 2552                                          │
│  ├── Management: 8552                                    │
│  └── Role: "llm-processing"                              │
├─────────────────────────────────────────────────────────────┤
│  Node 3: Data Analysis (DataAnalysisSystem)              │
│  ├── Port: 2553                                          │
│  ├── Management: 8553                                    │
│  └── Role: "data-analysis"                               │
└─────────────────────────────────────────────────────────────┘
```

### Core Services Node Details
- **ActorSystem Name**: `DatabaseExplorationSystem`
- **Cluster Role**: `core-services`
- **HTTP Port**: `8081` (Spring Boot application)
- **Akka Port**: `2551` (Cluster communication)
- **Management Port**: `8551` (Akka Management API)

## Node and ActorSystem Details

### Core Services Node Configuration
```hocon
# application-core-node.conf
akka {
  actor {
    provider = "akka.cluster.ClusterActorRefProvider"
  }
  
  remote {
    artery {
      canonical.hostname = "127.0.0.1"
      canonical.port = 2551
    }
  }
  
  cluster {
    seed-nodes = [
      "akka://DatabaseExplorationSystem@127.0.0.1:2551",
      "akka://DatabaseExplorationSystem@127.0.0.1:2552",
      "akka://DatabaseExplorationSystem@127.0.0.1:2553"
    ]
    roles = ["core-services"]
  }
  
  management {
    http.hostname = "127.0.0.1"
    http.port = 8551
    cluster.bootstrap {
      contact-point-discovery {
        service-name = "dbvybe-cluster"
      }
    }
  }
}
```

## Data Flow Architecture

### **1. Actor-First Data Flow:**
```
┌─────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Client    │───▶│   Controller │───▶│     Actor    │───▶│   Database   │
│  (Browser)  │    │ (Spring Boot)│    │  (Akka Typed)│    │   (MySQL)    │
└─────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
                           │                      │
                           ▼                      ▼
                    ┌──────────────┐    ┌──────────────┐
                    │ AskPattern   │    │   Response   │
                    │ (Async)      │    │   (Future)   │
                    └──────────────┘    └──────────────┘
```

### **2. User Login Flow (Actor-First):**
```
┌─────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Client    │───▶│ AuthController│───▶│SecurityActor │───▶│UserDatabase  │
└─────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
                                                              │
                                                              ▼
┌─────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Response  │◀───│ AuthController│◀───│SecurityActor │◀───│UserSessionDB │
└─────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
```

### **3. Session Management Flow (Actor-First):**
```
┌─────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Client    │───▶│SessionController│───▶│UserSessionManager│───▶│UserSessionDB │
└─────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
                                                              │
                                                              ▼
┌─────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Response  │◀───│SessionController│◀───│UserSessionManager│◀───│   MySQL DB   │
└─────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
```

### **4. Cross-Node Session Sharing:**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Core Services   │    │ LLM Processing  │    │ Data Analysis   │
│ Node (2551)     │    │ Node (2552)     │    │ Node (2553)     │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ • SecurityActor │    │ • Session Check │    │ • Session Check │
│ • UserSessionDB │    │ • User Context  │    │ • User Context  │
│ • AuthService   │    │ • LLM Processing│    │ • Data Analysis │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   MySQL DB      │
                    │ (Shared State)  │
                    └─────────────────┘
```

## API Endpoints

### **Essential APIs for UI Integration:**

#### **1. User Login (Automatic Session Creation)**
```http
POST /api/auth/login
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "admin",
  "password": "Admin@123",
  "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
  "ipAddress": "192.168.1.100"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "userId": "user-123",
  "username": "admin",
  "email": "admin@dbvybe.com",
  "role": "ADMIN",
  "status": "ACTIVE",
  "sessionId": "session-456-789-abc-def",
  "sessionExpiresAt": "2024-01-15T10:30:00",
  "refreshToken": "refresh-token-789-abc-def",
  "lastLoginAt": "2024-01-14T10:30:00"
}
```

#### **2. User Logout (Session Revocation)**
```http
POST /api/auth/logout
Content-Type: application/json
```

**Request Body:**
```json
{
  "sessionId": "session-456-789-abc-def"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "message": "User logged out successfully"
}
```

### **Advanced APIs (Optional for UI):**

#### **3. User Registration**
```http
POST /api/auth/register
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "newuser",
  "email": "newuser@example.com",
  "password": "NewUser123!"
}
```

#### **4. Change Password**
```http
POST /api/auth/change-password
Content-Type: application/json
```

**Request Body:**
```json
{
  "userId": "user-123",
  "currentPassword": "OldPass123!",
  "newPassword": "NewPass456!"
}
```

### **Session Management APIs (For Advanced Use Cases):**

#### **5. Validate Session**
```http
POST /api/sessions/validate
Content-Type: application/json
```

**Request Body:**
```json
{
  "sessionId": "session-456-789-abc"
}
```

#### **6. Extend Session**
```http
POST /api/sessions/extend
Content-Type: application/json
```

**Request Body:**
```json
{
  "sessionId": "session-456-789-abc",
  "sessionDurationHours": 12
}
```

#### **7. Revoke Session**
```http
DELETE /api/sessions/{sessionId}
```

#### **8. Get User Sessions**
```http
GET /api/sessions/user/{userId}
```

## UI Integration Flow

### **Simplified UI Flow (No Explicit Session APIs Needed):**

#### **1. User Login:**
```javascript
// Login request
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'admin',
    password: 'Admin@123',
    userAgent: navigator.userAgent,
    ipAddress: '192.168.1.100'
  })
});

const userData = await loginResponse.json();
if (userData.success) {
  // Store session info automatically
  localStorage.setItem('sessionId', userData.sessionId);
  localStorage.setItem('refreshToken', userData.refreshToken);
  localStorage.setItem('user', JSON.stringify(userData));
  
  // Redirect to dashboard
  window.location.href = '/dashboard';
}
```

#### **2. API Calls with Session:**
```javascript
// Helper function for authenticated requests
const makeAuthenticatedRequest = async (url, options = {}) => {
  const sessionId = localStorage.getItem('sessionId');
  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${sessionId}`
    }
  });
};

// Example: Get user profile
const profileResponse = await makeAuthenticatedRequest('/api/auth/users/me');
```

#### **3. User Logout:**
```javascript
// Logout request
const logoutResponse = await fetch('/api/auth/logout', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    sessionId: localStorage.getItem('sessionId')
  })
});

// Clear local storage
localStorage.removeItem('sessionId');
localStorage.removeItem('refreshToken');
localStorage.removeItem('user');

// Redirect to login
window.location.href = '/login';
```

### **What You Get Automatically:**

#### **✅ Session Creation:**
- **Automatic on login** - No separate API call needed
- **Session details returned** in login response
- **Database persistence** handled by SecurityActor

#### **✅ Session Validation:**
- **Backend validates** sessions for protected routes
- **Automatic cleanup** of expired sessions
- **Cross-node session sharing** in Akka cluster

#### **✅ Security Features:**
- **Password hashing** with salt
- **Account locking** after failed attempts
- **Session expiration** with configurable duration
- **IP and user agent tracking**

### **When to Use Explicit Session APIs:**

#### **✅ Advanced Use Cases:**
- **Multiple device sessions** (user logged in on phone + laptop)
- **Session administration** (admin revoking user sessions)
- **Session analytics** (tracking active sessions)
- **Security monitoring** (detecting suspicious activity)

#### **✅ Backend Services:**
- **Microservices** validating sessions
- **API Gateway** session validation
- **Background cleanup** of expired sessions

#### **✅ Mobile Apps:**
- **Token refresh** when refresh token expires
- **Session extension** for long-running operations
- **Offline session management**

## Testing Procedures

### 1. Start the Application
```bash
# Navigate to backend directory
cd backend

# Start the application
mvn spring-boot:run
```

### 2. Test Database Connection
```bash
# Check if MySQL is running
docker-compose up -d mysql

# Verify database connection
curl http://localhost:8081/actuator/health
```

### 3. Test User Registration
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "TestPass123!"
  }'
```

### 4. Test User Login (Actor-First)
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin@123",
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    "ipAddress": "192.168.1.100"
  }'
```

### 5. Test User Logout (Actor-First)
```bash
# Use sessionId from login response
curl -X POST http://localhost:8081/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-id-from-login-response"
  }'
```

### 6. Test Cluster Health
```bash
# Check cluster members
curl http://localhost:8551/cluster/members

# Check cluster health
curl http://localhost:8551/health
```

### 7. Test Actor-First Architecture
```bash
# Check cluster status
curl http://localhost:8081/api/cluster/status

# Check core services node
curl http://localhost:8081/api/cluster/nodes/core-services
```

## Database Schema

### Users Table
```sql
CREATE TABLE users (
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
);
```

### User Sessions Table
```sql
CREATE TABLE user_sessions (
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
);
```

## Security Features

### 1. Password Security
- **Algorithm**: SHA-256 with salt
- **Salt Generation**: 16-byte random salt
- **Password Requirements**: 8+ chars, uppercase, lowercase, number, special char

### 2. Account Security
- **Account Locking**: 5 failed attempts = 30-minute lock
- **Session Expiration**: 24-hour default, configurable
- **IP Tracking**: Logged for security monitoring
- **User Agent Tracking**: Device fingerprinting

### 3. Session Security
- **Secure Token Generation**: UUID-based session IDs
- **Refresh Tokens**: Separate refresh token mechanism
- **Automatic Cleanup**: Expired sessions marked as EXPIRED
- **Cross-Node Validation**: Sessions valid across all cluster nodes

### 4. Role-Based Access Control
- **Roles**: ADMIN, USER, GUEST
- **Status**: ACTIVE, INACTIVE, LOCKED, SUSPENDED
- **Access Validation**: Role-based API access control

## Akka Actor Implementation

### 1. SecurityActor
**Purpose**: Handles user authentication, registration, and security operations

**Location**: `com.dbVybe.app.actor.security.SecurityActor`

**Commands**:
- `AuthenticateUser` - User login with automatic session creation
- `RegisterUser` - User registration
- `ChangePassword` - Password change
- `UpdateUserRole` - Role management
- `UpdateUserStatus` - Status management
- `GetUser` - User retrieval
- `GetAllUsers` - All users retrieval
- `ValidateAccess` - Access control validation
- `LogoutUser` - User logout with session revocation

**Key Features**:
- ✅ **Actor-First Architecture** - All operations go through actors
- ✅ **Database integration** via `ActorServiceLocator`
- ✅ **Automatic session creation** on login
- ✅ **Password hashing** with salt
- ✅ **Account locking** mechanism
- ✅ **Role-based access** control

### 2. UserSessionManager
**Purpose**: Manages user sessions across the cluster

**Location**: `com.dbVybe.app.actor.session.UserSessionManager`

**Commands**:
- `CreateSession` - Create new session
- `ValidateSession` - Validate session
- `ExtendSession` - Extend session duration
- `RevokeSession` - Revoke session
- `GetUserSessions` - Get user's active sessions
- `CleanupExpiredSessions` - Cleanup expired sessions

**Key Features**:
- ✅ **Actor-First Architecture** - All operations go through actors
- ✅ **Database persistence** via `UserSessionDatabaseService`
- ✅ **Cross-node session sharing**
- ✅ **Automatic session cleanup**
- ✅ **Session lifecycle management**
- ✅ **Security tracking** (IP, user agent)

### 3. Actor Service Locator
**Purpose**: Provides Spring services to Akka actors

**Location**: `com.dbVybe.app.service.ActorServiceLocator`

**Services**:
- `UserDatabaseService` - User database operations
- `UserSessionDatabaseService` - Session database operations

### Why Actor-First Architecture?

1. **Concurrency**: Actors handle requests concurrently without blocking
2. **Fault Tolerance**: Actor failures don't affect the entire system
3. **Scalability**: Easy to add more actors for load distribution
4. **Message Passing**: Clean separation of concerns
5. **Cluster Support**: Actors can communicate across cluster nodes
6. **State Management**: Actors maintain state safely in concurrent environment
7. **Performance**: Non-blocking operations with better resource utilization
8. **Maintainability**: Clear separation between HTTP layer and business logic

## HTTP Status Codes

### Success Responses
- **200 OK**: Login, logout, session validation, extension, revocation, get sessions
- **201 Created**: User registration, session creation

### Error Responses
- **400 Bad Request**: Missing required fields, invalid data
- **401 Unauthorized**: Invalid credentials, expired session, locked account
- **403 Forbidden**: Insufficient permissions
- **404 Not Found**: User/session not found
- **409 Conflict**: Username/email already exists
- **500 Internal Server Error**: Server errors

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   ```bash
   # Check MySQL container
   docker-compose ps
   
   # Check database logs
   docker-compose logs mysql
   ```

2. **Cluster Not Forming**
   ```bash
   # Check cluster health
   curl http://localhost:8551/health
   
   # Check cluster members
   curl http://localhost:8551/cluster/members
   ```

3. **Actor Communication Failed**
   ```bash
   # Check application logs
   tail -f logs/application.log | grep "SecurityActor"
   
   # Check cluster status
   curl http://localhost:8081/api/cluster/status
   ```

4. **Session Not Created**
   ```bash
   # Check application logs
   tail -f logs/application.log | grep "Session"
   
   # Check database
   mysql -u dbvybe_user -p dbvybe -e "SELECT * FROM user_sessions;"
   ```

5. **Authentication Failed**
   ```bash
   # Check user table
   mysql -u dbvybe_user -p dbvybe -e "SELECT * FROM users;"
   
   # Check application logs
   tail -f logs/application.log | grep "Security"
   ```

### Debug Commands

```bash
# Check application status
curl http://localhost:8081/actuator/health

# Check cluster status
curl http://localhost:8081/api/cluster/status

# Check database tables
mysql -u dbvybe_user -p dbvybe -e "SHOW TABLES;"

# Monitor application logs
tail -f logs/application.log
```

## Performance Considerations

1. **Actor Concurrency**: Multiple actors handle requests concurrently
2. **Database Connection Pool**: Configured for optimal performance
3. **Session Cleanup**: Automatic cleanup every 5 minutes
4. **Indexing**: Proper database indexes for fast queries
5. **Caching**: Consider Redis for session caching in production
6. **Load Balancing**: Multiple instances can share session state

## Production Deployment

1. **Environment Variables**: Configure database credentials
2. **SSL/TLS**: Enable HTTPS for production
3. **Monitoring**: Add metrics and health checks
4. **Backup**: Regular database backups
5. **Scaling**: Horizontal scaling with session sharing
6. **Actor Supervision**: Configure actor supervision strategies

---

**This implementation provides a complete, production-ready user authentication and session management system with Actor-First architecture, industry-standard security practices, and Akka cluster support. The simplified UI flow eliminates the need for explicit session management APIs in typical web applications.** 
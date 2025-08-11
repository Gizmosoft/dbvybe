# Core Services Node – Database Actor Flow and API Guide

This document explains the end-to-end program flow for database connection management using the actor-first approach in the Core Services Node, and lists the public API endpoints.

## Overview

- The HTTP layer (`DatabaseController`) never talks to databases directly.
- All database connection lifecycle operations are delegated to the Akka Typed actor `DatabaseCommunicationManager` using the ask-pattern.
- Active connections are held in-memory by the actor and persisted as saved connection metadata in MySQL via `UserDatabaseConnectionService`.

## Key Components

- `DatabaseController` (REST)
  - Forwards requests to the actor using ask-pattern.
- `DatabaseCommunicationManager` (Akka Typed actor)
  - Validates requests, creates/closes connections, tracks active connections, persists metadata.
  - Active state: `Map<String, DatabaseConnection> activeConnections`
  - On shutdown: closes and clears all active connections.
- `DatabaseConnection` implementations
  - `PostgreSQLConnection`, `MySQLConnection`, `MongoDBConnection`
  - Open real driver connections and implement `test()`, `close()`, `isActive()`.
- `UserDatabaseConnectionService` (JDBC to MySQL)
  - Persists saved connections, queries user’s connections, soft-deletes (is_active = FALSE), hard-deletes rows.
- `ClusterManager` / `DatabaseExplorationSystem`
  - Boots the actor system at app start; terminates it at shutdown.

## Startup and Shutdown

- Startup (`ClusterManager.start` → `DatabaseExplorationSystem.start`)
  - Spawns `DatabaseCommunicationManager` and other actors; exposes scheduler for ask-pattern.
- Shutdown
  - Spring `@PreDestroy` stops the actor system.
  - `DatabaseCommunicationManager` handles `PostStop` and closes all active connections and clears its in-memory map.

## API Endpoints

Base path: `/api/database`

1) POST `/connect`
- Purpose: Establish and save a user-specific connection; creates a live connection and persists metadata.
- Request body:
  ```json
  {
    "userId": "string",
    "connectionName": "string",
    "databaseType": "POSTGRESQL | MYSQL | MONGODB",
    "host": "string",
    "port": 5432,
    "databaseName": "string",
    "username": "string",
    "password": "string",
    "additionalProperties": { "key": "value" }
  }
  ```
- Validations (failure if missing/invalid): `userId`, `connectionName`, `databaseType`, `host`, `port > 0`, `databaseName`, and databaseType must be supported.
- Response: 201 with connectionId on success; 4xx/5xx with message on failure.

2) POST `/connect-saved`
- Purpose: Create a live connection from a saved connection record.
- Request body:
  ```json
  { "connectionId": "string", "userId": "string" }
  ```
- Response: 200 on success; 404 if saved connection not found.

3) GET `/connections?userId={userId}`
- Purpose: List all active (is_active = TRUE) saved connections for a user.
- Response:
  ```json
  { "connections": [ ... ], "message": "Found N saved connections", "success": true }
  ```

4) POST `/test`
- Purpose: Validate connectivity without saving or keeping a live connection.
- Request body: same as POST `/connect`.
- Response: 200 if test succeeds; 4xx/5xx otherwise.

5) DELETE `/connect/{connectionId}?userId={userId}`
- Purpose: Close the in-memory active connection and soft-delete the saved record (is_active = FALSE).
- Response: 200 on “closed and deactivated”; 404 if not found/already inactive.

6) DELETE `/saved/{connectionId}?userId={userId}`
- Purpose: Close any active connection, soft-delete the saved record, then hard-delete the row from MySQL.
- Response: 200 on “deleted”; 404 if not found or delete failed.

7) GET `/connect/{connectionId}?userId={userId}`
- Purpose: Check if the connection is currently active in memory.
- Response: 200 “Connection is active” with original request; 404 “Active connection not found”.

## Program Flow (High-Level)

- In all cases, the controller uses ask-pattern to send a typed command to `DatabaseCommunicationManager` and await a typed reply.

A) POST `/connect`
1. Controller sends `EstablishConnection(request)`.
2. Actor validates request, checks for duplicate `connectionName` for user.
3. Creates a driver-specific `DatabaseConnection` and stores it in `activeConnections`.
4. Persists `UserDatabaseConnection` in MySQL (is_active = TRUE).
5. Returns success with `connectionId`.

B) POST `/connect-saved`
1. Controller sends `ConnectToSavedConnection(connectionId, userId)`.
2. Actor loads the saved record; builds a `DatabaseConnectionRequest`.
3. Creates a live connection; stores in `activeConnections`.
4. Updates `last_used_at`.
5. Returns success.

C) GET `/connections`
1. Controller sends `GetUserConnections(userId)`.
2. Actor calls service to fetch rows where `user_id = ?` and `is_active = TRUE`.
3. Returns list and message.

D) POST `/test`
1. Controller sends `TestConnection(request)`.
2. Actor validates and attempts to connect; closes immediately.
3. Returns test result.

E) DELETE `/connect/{connectionId}?userId=...`
1. Controller sends `CloseConnection(connectionId, userId)`.
2. Actor removes and closes any live connection.
3. Soft-deletes row: `is_active = FALSE`.
4. Returns success or relevant error.

F) DELETE `/saved/{connectionId}?userId=...`
1. Controller sends `DeleteSavedConnection(connectionId, userId)`.
2. Actor removes and closes any live connection.
3. Soft-deletes row (best-effort), then hard-deletes row from MySQL.
4. Returns success or relevant error.

G) GET `/connect/{connectionId}?userId=...`
1. Controller sends `GetConnectionStatus(connectionId, userId)`.
2. Actor checks `activeConnections` and returns status.

## Error Handling and Status Codes

- Validation errors → 400.
- Missing saved connection → 404.
- Connectivity failures or timeouts → 503.
- Unexpected runtime errors → 500.

## Curl Examples

- Establish connection:
  ```bash
  curl -X POST http://localhost:8081/api/database/connect \
    -H "Content-Type: application/json" \
    -d '{ "userId":"alice", "connectionName":"pg-main", "databaseType":"POSTGRESQL", "host":"localhost", "port":5432, "databaseName":"appdb", "username":"u", "password":"p", "additionalProperties":{"sslmode":"disable"}}'
  ```

- List saved (active) connections:
  ```bash
  curl "http://localhost:8081/api/database/connections?userId=alice"
  ```

- Connect to saved:
  ```bash
  curl -X POST http://localhost:8081/api/database/connect-saved \
    -H "Content-Type: application/json" \
    -d '{ "connectionId":"<id>", "userId":"alice" }'
  ```

- Close live + soft-delete saved:
  ```bash
  curl -X DELETE "http://localhost:8081/api/database/connect/<id>?userId=alice"
  ```

- Close live + soft-delete + hard-delete saved:
  ```bash
  curl -X DELETE "http://localhost:8081/api/database/saved/<id>?userId=alice"
  ```

- Get connection status:
  ```bash
  curl "http://localhost:8081/api/database/connect/<id>?userId=alice"
  ```

## Notes

- Saved list shows only `is_active = TRUE` rows; soft-deleted connections won’t appear.
- On shutdown (`PostStop`), the actor closes all live connections and clears in-memory state.
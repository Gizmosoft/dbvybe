# Akka Cluster Setup for dbVybe

This document describes the Akka Cluster setup with 3 nodes for the dbVybe project.

## Cluster Architecture

The cluster consists of 3 nodes, each with a specific role:

### Node 1: Core Services Node (DatabaseExplorationSystem)
- **Port**: 2551 (Akka), 8551 (Management)
- **Role**: `core-services`
- **Responsibilities**:
  - Database connection management
  - Query execution
  - Schema exploration
  - Core database operations

### Node 2: LLM Processing Node (LLMProcessingSystem)
- **Port**: 2552 (Akka), 8552 (Management)
- **Role**: `llm-processing`
- **Responsibilities**:
  - Natural language query processing
  - Query translation to SQL
  - LLM model management
  - Response generation

### Node 3: Data Analysis Node (DataAnalysisSystem)
- **Port**: 2553 (Akka), 8553 (Management)
- **Role**: `data-analysis`
- **Responsibilities**:
  - Data analysis and insights
  - Statistical processing
  - Data visualization preparation
  - Pattern recognition

## Configuration Files

Each node has its own configuration file:

- `application-core-node.conf` - Core Services Node configuration
- `application-llm-node.conf` - LLM Processing Node configuration
- `application-data-analysis-node.conf` - Data Analysis Node configuration

## Actor Hierarchy

Each node implements a basic actor hierarchy with a supervisor actor:

### DatabaseExplorationSystem
```
DatabaseExplorationSystem
└── database-exploration-supervisor
    ├── connection-manager (TODO)
    ├── query-executor (TODO)
    └── schema-explorer (TODO)
```

### LLMProcessingSystem
```
LLMProcessingSystem
└── llm-processing-supervisor
    ├── query-processor (TODO)
    ├── sql-translator (TODO)
    └── response-generator (TODO)
```

### DataAnalysisSystem
```
DataAnalysisSystem
└── data-analysis-supervisor
    ├── data-analyzer (TODO)
    ├── statistical-processor (TODO)
    └── pattern-recognizer (TODO)
```

## API Endpoints

The cluster provides REST endpoints for monitoring:

- `GET /api/cluster/status` - Get overall cluster health status
- `GET /api/cluster/nodes/{nodeName}` - Get detailed node information

### Node Names for API:
- `database-exploration` or `core-services` - Core Services Node
- `llm-processing` - LLM Processing Node
- `data-analysis` - Data Analysis Node

## Building and Running

### Prerequisites
- Java 17
- Maven 3.6+

### Build the Project
```bash
cd backend
mvn clean compile
```

### Run the Application
```bash
mvn spring-boot:run
```

The application will start all three cluster nodes automatically.

## Cluster Management

The `ClusterManager` class handles:
- Automatic startup of all three nodes
- Graceful shutdown of all nodes
- Health monitoring
- Node coordination

## Monitoring

### Cluster Status
```bash
curl http://localhost:8080/api/cluster/status
```

### Node Information
```bash
curl http://localhost:8080/api/cluster/nodes/core-services
curl http://localhost:8080/api/cluster/nodes/llm-processing
curl http://localhost:8080/api/cluster/nodes/data-analysis
```

## Development Notes

### Current State
- ✅ Basic actor hierarchy structure implemented
- ✅ Cluster configuration files created
- ✅ Node startup/shutdown logic implemented
- ✅ REST API for monitoring created
- ⏳ Actor implementations (marked as TODO)
- ⏳ Inter-node communication
- ⏳ Error handling and recovery

### Next Steps
1. Implement specific actors in each supervisor
2. Add inter-node communication protocols
3. Implement error handling and recovery mechanisms
4. Add metrics and monitoring
5. Implement load balancing and routing

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Ensure ports 2551-2553 and 8551-8553 are available
2. **Dependencies**: Run `mvn clean compile` to download Akka dependencies
3. **Cluster Formation**: Check logs for cluster membership messages

### Logs
Monitor application logs for:
- Cluster membership changes
- Node startup/shutdown messages
- Actor creation messages

## Dependencies

The project uses:
- Akka Typed 2.9.0
- Akka Cluster 2.9.0
- Akka Management 1.5.0
- Spring Boot 3.5.4
- Java 17 
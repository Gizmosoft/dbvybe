package com.dbVybe.app.service.agent;

import com.dbVybe.app.domain.model.DatabaseType;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Graph Analysis Agent - Manages database relationships in Neo4j graph database
 * 
 * This agent:
 * - Stores and retrieves database schema relationships in Neo4j
 * - Analyzes table relationships and foreign key dependencies
 * - Provides graph-based context for query generation
 * - Supports relationship discovery and path analysis
 */
@Service
public class GraphAnalysisAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphAnalysisAgent.class);
    
    // Neo4j Cloud Configuration
    @Value("${neo4j.uri:neo4j+s://your-instance-id.databases.neo4j.io}")
    private String neo4jUri;
    
    @Value("${neo4j.username:neo4j}")
    private String neo4jUsername;
    
    @Value("${neo4j.password:your-password}")
    private String neo4jPassword;
    
    @Value("${neo4j.database:neo4j}")
    private String neo4jDatabase;
    
    // Neo4j Driver
    private Driver driver;
    
    // Analysis Statistics
    private long totalRelationshipsStored = 0;
    private long totalGraphQueries = 0;
    private long totalPathAnalyses = 0;
    
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing Graph Analysis Agent with Neo4j Cloud connection: {}", neo4jUri);
            
            // Create Neo4j driver with cloud-specific configuration
            Config config = Config.builder()
                .withMaxConnectionLifetime(30, java.util.concurrent.TimeUnit.MINUTES)
                .withMaxConnectionPoolSize(50)
                .withConnectionAcquisitionTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
                .withTrustStrategy(Config.TrustStrategy.trustSystemCertificates())
                .build();
            
            this.driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword), config);
            
            // Test connection
            try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                session.run("RETURN 1").consume();
                logger.info("Neo4j Cloud connection established successfully");
            }
            
            // Initialize graph schema
            initializeGraphSchema();
            
        } catch (Exception e) {
            logger.error("Failed to initialize Graph Analysis Agent: {}", e.getMessage(), e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            driver.close();
            logger.info("Neo4j driver closed");
        }
    }
    
    /**
     * Store database schema relationships in Neo4j
     */
    public CompletableFuture<GraphStorageResult> storeSchemaRelationships(
            String connectionId, 
            String userId, 
            DatabaseType databaseType,
            List<TableRelationship> relationships) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Storing {} schema relationships for connection {} (user: {}, type: {})", 
                    relationships.size(), connectionId, userId, databaseType);
                
                try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                    // Start transaction
                    return session.executeWrite(tx -> {
                        try {
                            // Create or update database node
                            createDatabaseNode((Transaction) tx, connectionId, userId, databaseType);
                            
                            // Store table relationships
                            for (TableRelationship relationship : relationships) {
                                storeTableRelationship((Transaction) tx, connectionId, relationship);
                            }
                            
                            totalRelationshipsStored += relationships.size();
                            
                            logger.info("Successfully stored {} relationships for connection {}", 
                                relationships.size(), connectionId);
                            
                            return new GraphStorageResult(true, null, relationships.size());
                            
                        } catch (Exception e) {
                            logger.error("Error storing relationships: {}", e.getMessage(), e);
                            return new GraphStorageResult(false, e.getMessage(), 0);
                        }
                    });
                }
                
            } catch (Exception e) {
                logger.error("Failed to store schema relationships: {}", e.getMessage(), e);
                return new GraphStorageResult(false, e.getMessage(), 0);
            }
        });
    }
    
    /**
     * Find relationship paths between tables
     */
    public CompletableFuture<RelationshipPathResult> findRelationshipPaths(
            String connectionId, 
            String sourceTable, 
            String targetTable, 
            int maxDepth) {
        
        return CompletableFuture.supplyAsync(() -> {
            totalPathAnalyses++;
            
            try {
                logger.info("Finding relationship paths from {} to {} in connection {} (max depth: {})", 
                    sourceTable, targetTable, connectionId, maxDepth);
                
                try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                    String cypher = """
                        MATCH (db:Database {connectionId: $connectionId})
                        MATCH (source:Table {name: $sourceTable})-[:BELONGS_TO]->(db)
                        MATCH (target:Table {name: $targetTable})-[:BELONGS_TO]->(db)
                        MATCH path = shortestPath((source)-[*1..$maxDepth]-(target))
                        RETURN path, length(path) as pathLength
                        ORDER BY pathLength
                        LIMIT 10
                        """;
                    
                    Result result = session.run(cypher, 
                        Values.parameters(
                            "connectionId", connectionId,
                            "sourceTable", sourceTable,
                            "targetTable", targetTable,
                            "maxDepth", maxDepth
                        ));
                    
                    List<RelationshipPath> paths = new ArrayList<>();
                    while (result.hasNext()) {
                        Record record = result.next();
                        Path path = record.get("path").asPath();
                        int pathLength = record.get("pathLength").asInt();
                        
                        RelationshipPath relationshipPath = convertToRelationshipPath(path, pathLength);
                        paths.add(relationshipPath);
                    }
                    
                    logger.info("Found {} relationship paths from {} to {}", paths.size(), sourceTable, targetTable);
                    
                    return new RelationshipPathResult(paths, true, null);
                }
                
            } catch (Exception e) {
                logger.error("Error finding relationship paths: {}", e.getMessage(), e);
                return new RelationshipPathResult(Collections.emptyList(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Get related tables for query context
     */
    public CompletableFuture<RelatedTablesGraphResult> getRelatedTables(String connectionId, String tableName, int maxDepth) {
        return CompletableFuture.supplyAsync(() -> {
            totalGraphQueries++;
            
            try {
                logger.info("Getting related tables for {} in connection {} (max depth: {})", 
                    tableName, connectionId, maxDepth);
                
                try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                    String cypher = """
                        MATCH (db:Database {connectionId: $connectionId})
                        MATCH (table:Table {name: $tableName})-[:BELONGS_TO]->(db)
                        MATCH (table)-[r*1..$maxDepth]-(related:Table)-[:BELONGS_TO]->(db)
                        RETURN DISTINCT related.name as tableName, 
                               related.description as description,
                               length(r) as distance,
                               type(r[0]) as relationshipType
                        ORDER BY distance, tableName
                        LIMIT 20
                        """;
                    
                    Result result = session.run(cypher, 
                        Values.parameters(
                            "connectionId", connectionId,
                            "tableName", tableName,
                            "maxDepth", maxDepth
                        ));
                    
                    List<RelatedTableInfo> relatedTables = new ArrayList<>();
                    while (result.hasNext()) {
                        Record record = result.next();
                        
                        RelatedTableInfo relatedTable = new RelatedTableInfo(
                            record.get("tableName").asString(),
                            record.get("description").asString(""),
                            record.get("distance").asInt(),
                            record.get("relationshipType").asString("")
                        );
                        relatedTables.add(relatedTable);
                    }
                    
                    logger.info("Found {} related tables for {}", relatedTables.size(), tableName);
                    
                    return new RelatedTablesGraphResult(relatedTables, true, null);
                }
                
            } catch (Exception e) {
                logger.error("Error getting related tables: {}", e.getMessage(), e);
                return new RelatedTablesGraphResult(Collections.emptyList(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Analyze table dependencies for query optimization
     */
    public CompletableFuture<DependencyAnalysisResult> analyzeDependencies(String connectionId, List<String> tableNames) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Analyzing dependencies for {} tables in connection {}", tableNames.size(), connectionId);
                
                Map<String, List<String>> dependencies = new HashMap<>();
                Map<String, Integer> dependencyCounts = new HashMap<>();
                
                try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                    for (String tableName : tableNames) {
                        String cypher = """
                            MATCH (db:Database {connectionId: $connectionId})
                            MATCH (table:Table {name: $tableName})-[:BELONGS_TO]->(db)
                            MATCH (table)-[:REFERENCES]->(dependent:Table)-[:BELONGS_TO]->(db)
                            RETURN dependent.name as dependentTable
                            """;
                        
                        Result result = session.run(cypher, 
                            Values.parameters("connectionId", connectionId, "tableName", tableName));
                        
                        List<String> tableDependencies = new ArrayList<>();
                        while (result.hasNext()) {
                            String dependentTable = result.next().get("dependentTable").asString();
                            tableDependencies.add(dependentTable);
                        }
                        
                        dependencies.put(tableName, tableDependencies);
                        dependencyCounts.put(tableName, tableDependencies.size());
                    }
                }
                
                return new DependencyAnalysisResult(dependencies, dependencyCounts, true, null);
                
            } catch (Exception e) {
                logger.error("Error analyzing dependencies: {}", e.getMessage(), e);
                return new DependencyAnalysisResult(Collections.emptyMap(), Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    /**
     * Delete all graph data for a specific database connection
     */
    public CompletableFuture<Boolean> deleteConnectionGraphData(String connectionId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (driver == null) {
                    logger.warn("Neo4j driver not available. Cannot delete graph data for connection: {}", connectionId);
                    return false;
                }
                
                logger.info("Deleting graph data for connection {} (user: {})", connectionId, userId);
                
                try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
                    return session.executeWrite(tx -> {
                        try {
                            // Delete all nodes and relationships for this database connection
                            String deleteQuery = """
                                MATCH (db:Database {connectionId: $connectionId, userId: $userId})
                                OPTIONAL MATCH (db)-[r1*]-(connected)
                                DETACH DELETE db, connected
                                RETURN count(*) as deletedCount
                                """;
                            
                            Result result = tx.run(deleteQuery, Values.parameters(
                                "connectionId", connectionId,
                                "userId", userId
                            ));
                            
                            Record record = result.single();
                            int deletedCount = record.get("deletedCount").asInt();
                            
                            logger.info("Successfully deleted {} graph nodes/relationships for connection {} (user: {})", 
                                deletedCount, connectionId, userId);
                            
                            return true;
                            
                        } catch (Exception e) {
                            logger.error("Error in Neo4j delete transaction for connection {} (user: {}): {}", 
                                connectionId, userId, e.getMessage(), e);
                            return false;
                        }
                    });
                }
                
            } catch (Exception e) {
                logger.error("Error deleting graph data for connection {} (user: {}): {}", 
                    connectionId, userId, e.getMessage(), e);
                return false;
            }
        });
    }
    
    /**
     * Get graph analysis statistics
     */
    public GraphAnalysisStats getStats() {
        return new GraphAnalysisStats(
            totalRelationshipsStored,
            totalGraphQueries,
            totalPathAnalyses,
            driver != null
        );
    }
    
    // Helper methods
    
    private void initializeGraphSchema() {
        try (Session session = driver.session(SessionConfig.forDatabase(neo4jDatabase))) {
            session.executeWrite(tx -> {
                // Create constraints and indexes
                tx.run("CREATE CONSTRAINT database_connection_id IF NOT EXISTS FOR (d:Database) REQUIRE d.connectionId IS UNIQUE");
                tx.run("CREATE INDEX table_name_index IF NOT EXISTS FOR (t:Table) ON (t.name)");
                tx.run("CREATE INDEX column_name_index IF NOT EXISTS FOR (c:Column) ON (c.name)");
                
                return null;
            });
            
            logger.info("Graph schema initialized");
        } catch (Exception e) {
            logger.warn("Could not initialize graph schema: {}", e.getMessage());
        }
    }
    
    private void createDatabaseNode(Transaction tx, String connectionId, String userId, DatabaseType databaseType) {
        String cypher = """
            MERGE (db:Database {connectionId: $connectionId})
            SET db.userId = $userId,
                db.databaseType = $databaseType,
                db.lastUpdated = datetime()
            """;
        
        tx.run(cypher, Values.parameters(
            "connectionId", connectionId,
            "userId", userId,
            "databaseType", databaseType.toString()
        ));
    }
    
    private void storeTableRelationship(Transaction tx, String connectionId, TableRelationship relationship) {
        // Create table nodes
        String createTablesCypher = """
            MATCH (db:Database {connectionId: $connectionId})
            MERGE (source:Table {name: $sourceTable})-[:BELONGS_TO]->(db)
            SET source.description = $sourceDescription
            MERGE (target:Table {name: $targetTable})-[:BELONGS_TO]->(db)
            SET target.description = $targetDescription
            """;
        
        tx.run(createTablesCypher, Values.parameters(
            "connectionId", connectionId,
            "sourceTable", relationship.getSourceTable(),
            "sourceDescription", relationship.getSourceDescription(),
            "targetTable", relationship.getTargetTable(),
            "targetDescription", relationship.getTargetDescription()
        ));
        
        // Create relationship
        String createRelationshipCypher = """
            MATCH (db:Database {connectionId: $connectionId})
            MATCH (source:Table {name: $sourceTable})-[:BELONGS_TO]->(db)
            MATCH (target:Table {name: $targetTable})-[:BELONGS_TO]->(db)
            MERGE (source)-[r:REFERENCES {
                sourceColumn: $sourceColumn,
                targetColumn: $targetColumn,
                relationshipType: $relationshipType
            }]->(target)
            SET r.createdAt = datetime()
            """;
        
        tx.run(createRelationshipCypher, Values.parameters(
            "connectionId", connectionId,
            "sourceTable", relationship.getSourceTable(),
            "targetTable", relationship.getTargetTable(),
            "sourceColumn", relationship.getSourceColumn(),
            "targetColumn", relationship.getTargetColumn(),
            "relationshipType", relationship.getRelationshipType()
        ));
    }
    
    private RelationshipPath convertToRelationshipPath(Path path, int pathLength) {
        List<String> tableNames = new ArrayList<>();
        List<String> relationshipTypes = new ArrayList<>();
        
        for (Node node : path.nodes()) {
            if (node.hasLabel("Table")) {
                tableNames.add(node.get("name").asString());
            }
        }
        
        for (Relationship rel : path.relationships()) {
            relationshipTypes.add(rel.type());
        }
        
        return new RelationshipPath(tableNames, relationshipTypes, pathLength);
    }
    
    // Data Classes
    
    public static class TableRelationship {
        private final String sourceTable;
        private final String targetTable;
        private final String sourceColumn;
        private final String targetColumn;
        private final String relationshipType;
        private final String sourceDescription;
        private final String targetDescription;
        
        public TableRelationship(String sourceTable, String targetTable, String sourceColumn, 
                               String targetColumn, String relationshipType, 
                               String sourceDescription, String targetDescription) {
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
            this.sourceColumn = sourceColumn;
            this.targetColumn = targetColumn;
            this.relationshipType = relationshipType;
            this.sourceDescription = sourceDescription;
            this.targetDescription = targetDescription;
        }
        
        public String getSourceTable() { return sourceTable; }
        public String getTargetTable() { return targetTable; }
        public String getSourceColumn() { return sourceColumn; }
        public String getTargetColumn() { return targetColumn; }
        public String getRelationshipType() { return relationshipType; }
        public String getSourceDescription() { return sourceDescription; }
        public String getTargetDescription() { return targetDescription; }
    }
    
    public static class GraphStorageResult {
        private final boolean success;
        private final String error;
        private final int relationshipsStored;
        
        public GraphStorageResult(boolean success, String error, int relationshipsStored) {
            this.success = success;
            this.error = error;
            this.relationshipsStored = relationshipsStored;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public int getRelationshipsStored() { return relationshipsStored; }
    }
    
    public static class RelationshipPathResult {
        private final List<RelationshipPath> paths;
        private final boolean success;
        private final String error;
        
        public RelationshipPathResult(List<RelationshipPath> paths, boolean success, String error) {
            this.paths = paths;
            this.success = success;
            this.error = error;
        }
        
        public List<RelationshipPath> getPaths() { return paths; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class RelationshipPath {
        private final List<String> tableNames;
        private final List<String> relationshipTypes;
        private final int pathLength;
        
        public RelationshipPath(List<String> tableNames, List<String> relationshipTypes, int pathLength) {
            this.tableNames = tableNames;
            this.relationshipTypes = relationshipTypes;
            this.pathLength = pathLength;
        }
        
        public List<String> getTableNames() { return tableNames; }
        public List<String> getRelationshipTypes() { return relationshipTypes; }
        public int getPathLength() { return pathLength; }
    }
    
    public static class RelatedTablesGraphResult {
        private final List<RelatedTableInfo> relatedTables;
        private final boolean success;
        private final String error;
        
        public RelatedTablesGraphResult(List<RelatedTableInfo> relatedTables, boolean success, String error) {
            this.relatedTables = relatedTables;
            this.success = success;
            this.error = error;
        }
        
        public List<RelatedTableInfo> getRelatedTables() { return relatedTables; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class RelatedTableInfo {
        private final String tableName;
        private final String description;
        private final int distance;
        private final String relationshipType;
        
        public RelatedTableInfo(String tableName, String description, int distance, String relationshipType) {
            this.tableName = tableName;
            this.description = description;
            this.distance = distance;
            this.relationshipType = relationshipType;
        }
        
        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public int getDistance() { return distance; }
        public String getRelationshipType() { return relationshipType; }
    }
    
    public static class DependencyAnalysisResult {
        private final Map<String, List<String>> dependencies;
        private final Map<String, Integer> dependencyCounts;
        private final boolean success;
        private final String error;
        
        public DependencyAnalysisResult(Map<String, List<String>> dependencies, Map<String, Integer> dependencyCounts, 
                                      boolean success, String error) {
            this.dependencies = dependencies;
            this.dependencyCounts = dependencyCounts;
            this.success = success;
            this.error = error;
        }
        
        public Map<String, List<String>> getDependencies() { return dependencies; }
        public Map<String, Integer> getDependencyCounts() { return dependencyCounts; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    public static class GraphAnalysisStats {
        private final long totalRelationshipsStored;
        private final long totalGraphQueries;
        private final long totalPathAnalyses;
        private final boolean neo4jConnected;
        
        public GraphAnalysisStats(long totalRelationshipsStored, long totalGraphQueries, 
                                long totalPathAnalyses, boolean neo4jConnected) {
            this.totalRelationshipsStored = totalRelationshipsStored;
            this.totalGraphQueries = totalGraphQueries;
            this.totalPathAnalyses = totalPathAnalyses;
            this.neo4jConnected = neo4jConnected;
        }
        
        public long getTotalRelationshipsStored() { return totalRelationshipsStored; }
        public long getTotalGraphQueries() { return totalGraphQueries; }
        public long getTotalPathAnalyses() { return totalPathAnalyses; }
        public boolean isNeo4jConnected() { return neo4jConnected; }
    }
}

package com.dbVybe.app.actor.analysis;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.service.agent.GraphAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GraphActor - Handles graph-based analysis of database relationships
 * 
 * This actor:
 * - Stores and retrieves database relationships in Neo4j
 * - Analyzes table dependencies and relationship paths
 * - Provides graph-based context for query generation
 * - Integrates with QueryGenerationActor and NLPAgent for enhanced relationship understanding
 */
public class GraphActor extends AbstractBehavior<GraphActor.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphActor.class);
    
    private final GraphAnalysisAgent graphAnalysisAgent;
    private final AtomicLong activeRequests = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    // Actor Commands
    public interface Command {}
    
    /**
     * Store schema relationships in graph database
     */
    public static class StoreSchemaRelationships implements Command {
        private final String connectionId;
        private final String userId;
        private final DatabaseType databaseType;
        private final List<GraphAnalysisAgent.TableRelationship> relationships;
        private final ActorRef<GraphStorageResponse> replyTo;
        
        public StoreSchemaRelationships(String connectionId, String userId, DatabaseType databaseType,
                                      List<GraphAnalysisAgent.TableRelationship> relationships,
                                      ActorRef<GraphStorageResponse> replyTo) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.databaseType = databaseType;
            this.relationships = relationships;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public DatabaseType getDatabaseType() { return databaseType; }
        public List<GraphAnalysisAgent.TableRelationship> getRelationships() { return relationships; }
        public ActorRef<GraphStorageResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Find relationship paths between tables
     */
    public static class FindRelationshipPaths implements Command {
        private final String connectionId;
        private final String sourceTable;
        private final String targetTable;
        private final int maxDepth;
        private final ActorRef<RelationshipPathsResponse> replyTo;
        
        public FindRelationshipPaths(String connectionId, String sourceTable, String targetTable, 
                                   int maxDepth, ActorRef<RelationshipPathsResponse> replyTo) {
            this.connectionId = connectionId;
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
            this.maxDepth = maxDepth;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getSourceTable() { return sourceTable; }
        public String getTargetTable() { return targetTable; }
        public int getMaxDepth() { return maxDepth; }
        public ActorRef<RelationshipPathsResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get related tables from graph
     */
    public static class GetRelatedTables implements Command {
        private final String connectionId;
        private final String tableName;
        private final int maxDepth;
        private final ActorRef<RelatedTablesGraphResponse> replyTo;
        
        public GetRelatedTables(String connectionId, String tableName, int maxDepth, 
                              ActorRef<RelatedTablesGraphResponse> replyTo) {
            this.connectionId = connectionId;
            this.tableName = tableName;
            this.maxDepth = maxDepth;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public String getTableName() { return tableName; }
        public int getMaxDepth() { return maxDepth; }
        public ActorRef<RelatedTablesGraphResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Analyze table dependencies
     */
    public static class AnalyzeDependencies implements Command {
        private final String connectionId;
        private final List<String> tableNames;
        private final ActorRef<DependencyAnalysisResponse> replyTo;
        
        public AnalyzeDependencies(String connectionId, List<String> tableNames, 
                                 ActorRef<DependencyAnalysisResponse> replyTo) {
            this.connectionId = connectionId;
            this.tableNames = tableNames;
            this.replyTo = replyTo;
        }
        
        public String getConnectionId() { return connectionId; }
        public List<String> getTableNames() { return tableNames; }
        public ActorRef<DependencyAnalysisResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get graph analysis statistics
     */
    public static class GetGraphStats implements Command {
        private final ActorRef<GraphStatsResponse> replyTo;
        
        public GetGraphStats(ActorRef<GraphStatsResponse> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<GraphStatsResponse> getReplyTo() { return replyTo; }
    }
    
    // Response Classes
    
    public static class GraphStorageResponse {
        private final boolean success;
        private final String error;
        private final int relationshipsStored;
        private final String requestId;
        
        public GraphStorageResponse(boolean success, String error, int relationshipsStored, String requestId) {
            this.success = success;
            this.error = error;
            this.relationshipsStored = relationshipsStored;
            this.requestId = requestId;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public int getRelationshipsStored() { return relationshipsStored; }
        public String getRequestId() { return requestId; }
    }
    
    public static class RelationshipPathsResponse {
        private final List<GraphAnalysisAgent.RelationshipPath> paths;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public RelationshipPathsResponse(List<GraphAnalysisAgent.RelationshipPath> paths, boolean success, 
                                       String error, String requestId) {
            this.paths = paths;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public List<GraphAnalysisAgent.RelationshipPath> getPaths() { return paths; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class RelatedTablesGraphResponse {
        private final List<GraphAnalysisAgent.RelatedTableInfo> relatedTables;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public RelatedTablesGraphResponse(List<GraphAnalysisAgent.RelatedTableInfo> relatedTables, 
                                        boolean success, String error, String requestId) {
            this.relatedTables = relatedTables;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public List<GraphAnalysisAgent.RelatedTableInfo> getRelatedTables() { return relatedTables; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class DependencyAnalysisResponse {
        private final java.util.Map<String, List<String>> dependencies;
        private final java.util.Map<String, Integer> dependencyCounts;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public DependencyAnalysisResponse(java.util.Map<String, List<String>> dependencies, 
                                        java.util.Map<String, Integer> dependencyCounts,
                                        boolean success, String error, String requestId) {
            this.dependencies = dependencies;
            this.dependencyCounts = dependencyCounts;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public java.util.Map<String, List<String>> getDependencies() { return dependencies; }
        public java.util.Map<String, Integer> getDependencyCounts() { return dependencyCounts; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class GraphStatsResponse {
        private final long activeRequests;
        private final long totalRequests;
        private final GraphAnalysisAgent.GraphAnalysisStats agentStats;
        private final boolean success;
        
        public GraphStatsResponse(long activeRequests, long totalRequests, 
                                GraphAnalysisAgent.GraphAnalysisStats agentStats, boolean success) {
            this.activeRequests = activeRequests;
            this.totalRequests = totalRequests;
            this.agentStats = agentStats;
            this.success = success;
        }
        
        public long getActiveRequests() { return activeRequests; }
        public long getTotalRequests() { return totalRequests; }
        public GraphAnalysisAgent.GraphAnalysisStats getAgentStats() { return agentStats; }
        public boolean isSuccess() { return success; }
    }
    
    public static Behavior<Command> create(GraphAnalysisAgent graphAnalysisAgent) {
        return Behaviors.setup(context -> new GraphActor(context, graphAnalysisAgent));
    }
    
    private GraphActor(ActorContext<Command> context, GraphAnalysisAgent graphAnalysisAgent) {
        super(context);
        this.graphAnalysisAgent = graphAnalysisAgent;
        logger.info("GraphActor created and ready for graph analysis operations");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(StoreSchemaRelationships.class, this::onStoreSchemaRelationships)
            .onMessage(FindRelationshipPaths.class, this::onFindRelationshipPaths)
            .onMessage(GetRelatedTables.class, this::onGetRelatedTables)
            .onMessage(AnalyzeDependencies.class, this::onAnalyzeDependencies)
            .onMessage(GetGraphStats.class, this::onGetGraphStats)
            .build();
    }
    
    private Behavior<Command> onStoreSchemaRelationships(StoreSchemaRelationships command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Storing {} schema relationships for connection {} (user: {}, type: {}) - Request ID: {}", 
            command.getRelationships().size(), command.getConnectionId(), command.getUserId(), 
            command.getDatabaseType(), requestId);
        
        graphAnalysisAgent.storeSchemaRelationships(
                command.getConnectionId(), 
                command.getUserId(), 
                command.getDatabaseType(), 
                command.getRelationships())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Store schema relationships failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new GraphStorageResponse(false, throwable.getMessage(), 0, requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Successfully stored {} relationships (Request ID: {})", 
                                result.getRelationshipsStored(), requestId);
                            command.getReplyTo().tell(new GraphStorageResponse(
                                true, null, result.getRelationshipsStored(), requestId));
                        } else {
                            logger.error("Store schema relationships failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new GraphStorageResponse(false, result.getError(), 0, requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in store relationships completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onFindRelationshipPaths(FindRelationshipPaths command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Finding relationship paths from {} to {} in connection {} (max depth: {}) - Request ID: {}", 
            command.getSourceTable(), command.getTargetTable(), command.getConnectionId(), 
            command.getMaxDepth(), requestId);
        
        graphAnalysisAgent.findRelationshipPaths(
                command.getConnectionId(), 
                command.getSourceTable(), 
                command.getTargetTable(), 
                command.getMaxDepth())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Find relationship paths failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new RelationshipPathsResponse(
                            java.util.Collections.emptyList(), false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Found {} relationship paths (Request ID: {})", result.getPaths().size(), requestId);
                            command.getReplyTo().tell(new RelationshipPathsResponse(
                                result.getPaths(), true, null, requestId));
                        } else {
                            logger.error("Find relationship paths failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new RelationshipPathsResponse(
                                java.util.Collections.emptyList(), false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in find paths completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetRelatedTables(GetRelatedTables command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Getting related tables for {} in connection {} (max depth: {}) - Request ID: {}", 
            command.getTableName(), command.getConnectionId(), command.getMaxDepth(), requestId);
        
        graphAnalysisAgent.getRelatedTables(command.getConnectionId(), command.getTableName(), command.getMaxDepth())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Get related tables failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new RelatedTablesGraphResponse(
                            java.util.Collections.emptyList(), false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Found {} related tables (Request ID: {})", result.getRelatedTables().size(), requestId);
                            command.getReplyTo().tell(new RelatedTablesGraphResponse(
                                result.getRelatedTables(), true, null, requestId));
                        } else {
                            logger.error("Get related tables failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new RelatedTablesGraphResponse(
                                java.util.Collections.emptyList(), false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in get related tables completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onAnalyzeDependencies(AnalyzeDependencies command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Analyzing dependencies for {} tables in connection {} - Request ID: {}", 
            command.getTableNames().size(), command.getConnectionId(), requestId);
        
        graphAnalysisAgent.analyzeDependencies(command.getConnectionId(), command.getTableNames())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Analyze dependencies failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new DependencyAnalysisResponse(
                            java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), 
                            false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Dependency analysis completed for {} tables (Request ID: {})", 
                                result.getDependencies().size(), requestId);
                            command.getReplyTo().tell(new DependencyAnalysisResponse(
                                result.getDependencies(), result.getDependencyCounts(), true, null, requestId));
                        } else {
                            logger.error("Analyze dependencies failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new DependencyAnalysisResponse(
                                java.util.Collections.emptyMap(), java.util.Collections.emptyMap(), 
                                false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in analyze dependencies completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetGraphStats(GetGraphStats command) {
        logger.debug("Providing graph analysis statistics");
        
        try {
            GraphAnalysisAgent.GraphAnalysisStats agentStats = graphAnalysisAgent.getStats();
            
            command.getReplyTo().tell(new GraphStatsResponse(
                activeRequests.get(),
                totalRequests.get(),
                agentStats,
                true
            ));
        } catch (Exception e) {
            logger.error("Error getting graph stats: {}", e.getMessage(), e);
            command.getReplyTo().tell(new GraphStatsResponse(0, 0, null, false));
        }
        
        return Behaviors.same();
    }
}

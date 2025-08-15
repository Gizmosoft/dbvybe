package com.dbVybe.app.service.context;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import com.dbVybe.app.actor.analysis.VectorizationActor;
import com.dbVybe.app.actor.analysis.GraphActor;
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import com.dbVybe.app.cluster.ClusterManager;
import com.dbVybe.app.service.agent.VectorAnalysisAgent;
import com.dbVybe.app.service.agent.GraphAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Analysis Context Provider - Implementation of ContextProvider using vectorization and graph analysis
 * 
 * This service integrates with VectorizationActor and GraphActor to provide comprehensive
 * context information for query generation and NLP processing.
 */
@Service
public class AnalysisContextProvider implements ContextProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisContextProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final ClusterManager clusterManager;
    
    @Autowired
    public AnalysisContextProvider(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }
    
    @Override
    public CompletableFuture<VectorContext> getVectorContext(String naturalLanguageQuery, String connectionId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Getting vector context for query: '{}' (connection: {}, user: {})", 
                    naturalLanguageQuery, connectionId, userId);
                
                ActorRef<VectorizationActor.Command> vectorizationActor = clusterManager.getVectorizationActor();
                Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
                
                if (vectorizationActor == null) {
                    return new VectorContext(Collections.emptyList(), Collections.emptyList(), 
                        Collections.emptyMap(), false, "VectorizationActor not available");
                }
                
                CompletableFuture<VectorizationActor.QueryContextResponse> future = 
                    AskPattern.<VectorizationActor.Command, VectorizationActor.QueryContextResponse>ask(
                        vectorizationActor,
                        replyTo -> new VectorizationActor.GetQueryContext(naturalLanguageQuery, connectionId, userId, replyTo),
                        TIMEOUT,
                        scheduler
                    ).toCompletableFuture();
                
                VectorizationActor.QueryContextResponse response = future.get();
                
                if (response.isSuccess()) {
                    // Convert to ContextProvider format
                    List<ContextProvider.TableContext> tableContexts = response.getTableContexts().stream()
                        .map(this::convertToTableContext)
                        .collect(Collectors.toList());
                    
                    Map<String, Float> relevanceScores = response.getTableContexts().stream()
                        .collect(Collectors.toMap(
                            VectorAnalysisAgent.TableContext::getTableName,
                            VectorAnalysisAgent.TableContext::getRelevanceScore
                        ));
                    
                    logger.info("Retrieved vector context with {} table contexts", tableContexts.size());
                    
                    return new VectorContext(tableContexts, response.getSuggestedTables(), 
                        relevanceScores, true, null);
                } else {
                    return new VectorContext(Collections.emptyList(), Collections.emptyList(), 
                        Collections.emptyMap(), false, response.getError());
                }
                
            } catch (Exception e) {
                logger.error("Error getting vector context: {}", e.getMessage(), e);
                return new VectorContext(Collections.emptyList(), Collections.emptyList(), 
                    Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<GraphContext> getGraphContext(String connectionId, List<String> tableNames, int maxDepth) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Getting graph context for {} tables in connection {} (max depth: {})", 
                    tableNames.size(), connectionId, maxDepth);
                
                ActorRef<GraphActor.Command> graphActor = clusterManager.getGraphActor();
                Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
                
                if (graphActor == null) {
                    return new GraphContext(Collections.emptyMap(), Collections.emptyList(), 
                        Collections.emptyMap(), false, "GraphActor not available");
                }
                
                // Get dependencies for all tables
                CompletableFuture<GraphActor.DependencyAnalysisResponse> depFuture = 
                    AskPattern.<GraphActor.Command, GraphActor.DependencyAnalysisResponse>ask(
                        graphActor,
                        replyTo -> new GraphActor.AnalyzeDependencies(connectionId, tableNames, replyTo),
                        TIMEOUT,
                        scheduler
                    ).toCompletableFuture();
                
                GraphActor.DependencyAnalysisResponse depResponse = depFuture.get();
                
                if (depResponse.isSuccess()) {
                    // Get relationship paths between key tables
                    List<RelationshipPath> relationshipPaths = new ArrayList<>();
                    
                    // For now, we'll create a simple implementation
                    // In a full implementation, you'd analyze all table pairs
                    
                    logger.info("Retrieved graph context with {} dependencies", depResponse.getDependencies().size());
                    
                    return new GraphContext(depResponse.getDependencies(), relationshipPaths, 
                        depResponse.getDependencyCounts(), true, null);
                } else {
                    return new GraphContext(Collections.emptyMap(), Collections.emptyList(), 
                        Collections.emptyMap(), false, depResponse.getError());
                }
                
            } catch (Exception e) {
                logger.error("Error getting graph context: {}", e.getMessage(), e);
                return new GraphContext(Collections.emptyMap(), Collections.emptyList(), 
                    Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<CombinedContext> getCombinedContext(String naturalLanguageQuery, String connectionId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Getting combined context for query: '{}' (connection: {}, user: {})", 
                    naturalLanguageQuery, connectionId, userId);
                
                // Get vector context
                VectorContext vectorContext = getVectorContext(naturalLanguageQuery, connectionId, userId).get();
                
                // Extract table names from vector context for graph analysis
                List<String> relevantTables = vectorContext.getRelevantTables().stream()
                    .map(ContextProvider.TableContext::getTableName)
                    .collect(Collectors.toList());
                
                // Get graph context for relevant tables
                GraphContext graphContext = Collections.emptyList().equals(relevantTables) ? 
                    new GraphContext(Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(), true, null) :
                    getGraphContext(connectionId, relevantTables, 3).get();
                
                // Generate contextual hints and join recommendations
                List<String> recommendedJoinOrder = generateJoinOrder(vectorContext, graphContext);
                Map<String, String> contextualHints = generateContextualHints(vectorContext, graphContext, naturalLanguageQuery);
                
                boolean success = vectorContext.isSuccess() && graphContext.isSuccess();
                String error = !vectorContext.isSuccess() ? vectorContext.getError() : 
                             !graphContext.isSuccess() ? graphContext.getError() : null;
                
                logger.info("Generated combined context with {} vector tables and {} graph relationships", 
                    vectorContext.getRelevantTables().size(), graphContext.getTableRelationships().size());
                
                return new CombinedContext(vectorContext, graphContext, recommendedJoinOrder, 
                    contextualHints, success, error);
                
            } catch (Exception e) {
                logger.error("Error getting combined context: {}", e.getMessage(), e);
                return new CombinedContext(null, null, Collections.emptyList(), 
                    Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    @Override
    public CompletableFuture<SchemaContext> getSchemaContext(String connectionId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Getting schema context for connection: {} (user: {})", connectionId, userId);
                
                ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor = clusterManager.getSchemaAnalysisActor();
                Scheduler scheduler = clusterManager.getDataAnalysisScheduler();
                
                if (schemaAnalysisActor == null) {
                    return new SchemaContext(Collections.emptyMap(), Collections.emptyList(), 
                        Collections.emptyMap(), false, "SchemaAnalysisActor not available");
                }
                
                // Get schema analysis stats to understand available tables
                CompletableFuture<SchemaAnalysisActor.AnalysisStatsResponse> future = 
                    AskPattern.<SchemaAnalysisActor.Command, SchemaAnalysisActor.AnalysisStatsResponse>ask(
                        schemaAnalysisActor,
                        replyTo -> new SchemaAnalysisActor.GetAnalysisStats(replyTo),
                        TIMEOUT,
                        scheduler
                    ).toCompletableFuture();
                
                SchemaAnalysisActor.AnalysisStatsResponse response = future.get();
                
                if (response.isSuccess()) {
                    // For now, return basic schema context
                    // In a full implementation, you'd extract detailed schema information
                    
                    Map<String, TableSchema> tableSchemas = new HashMap<>();
                    List<String> availableTables = new ArrayList<>();
                    Map<String, List<String>> foreignKeyRelationships = new HashMap<>();
                    
                    logger.info("Retrieved schema context for connection {}", connectionId);
                    
                    return new SchemaContext(tableSchemas, availableTables, foreignKeyRelationships, true, null);
                } else {
                    return new SchemaContext(Collections.emptyMap(), Collections.emptyList(), 
                        Collections.emptyMap(), false, "Failed to get schema stats");
                }
                
            } catch (Exception e) {
                logger.error("Error getting schema context: {}", e.getMessage(), e);
                return new SchemaContext(Collections.emptyMap(), Collections.emptyList(), 
                    Collections.emptyMap(), false, e.getMessage());
            }
        });
    }
    
    // Helper methods
    
    private ContextProvider.TableContext convertToTableContext(VectorAnalysisAgent.TableContext agentContext) {
        List<String> columns = extractColumnsFromDescription(agentContext.getDescription());
        Map<String, String> columnTypes = extractColumnTypesFromDescription(agentContext.getDescription());
        
        return new ContextProvider.TableContext(
            agentContext.getTableName(),
            agentContext.getDescription(),
            columns,
            columnTypes,
            agentContext.getRelevanceScore()
        );
    }
    
    private List<String> extractColumnsFromDescription(String description) {
        // Extract column names from the description
        if (description.contains("Columns: ")) {
            String columnsSection = description.substring(description.indexOf("Columns: ") + 9);
            if (columnsSection.contains(".")) {
                columnsSection = columnsSection.substring(0, columnsSection.indexOf("."));
            }
            
            return Arrays.stream(columnsSection.split(","))
                .map(String::trim)
                .map(col -> col.contains("(") ? col.substring(0, col.indexOf("(")).trim() : col)
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
    
    private Map<String, String> extractColumnTypesFromDescription(String description) {
        Map<String, String> columnTypes = new HashMap<>();
        
        if (description.contains("Columns: ")) {
            String columnsSection = description.substring(description.indexOf("Columns: ") + 9);
            if (columnsSection.contains(".")) {
                columnsSection = columnsSection.substring(0, columnsSection.indexOf("."));
            }
            
            Arrays.stream(columnsSection.split(","))
                .map(String::trim)
                .forEach(col -> {
                    if (col.contains("(") && col.contains(")")) {
                        String columnName = col.substring(0, col.indexOf("(")).trim();
                        String columnType = col.substring(col.indexOf("(") + 1, col.indexOf(")")).trim();
                        columnTypes.put(columnName, columnType);
                    }
                });
        }
        
        return columnTypes;
    }
    
    private List<String> generateJoinOrder(VectorContext vectorContext, GraphContext graphContext) {
        // Generate recommended join order based on vector relevance and graph relationships
        List<String> joinOrder = new ArrayList<>();
        
        if (vectorContext.isSuccess() && !vectorContext.getRelevantTables().isEmpty()) {
            // Sort tables by relevance score
            joinOrder = vectorContext.getRelevantTables().stream()
                .sorted((a, b) -> Float.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .map(ContextProvider.TableContext::getTableName)
                .collect(Collectors.toList());
        }
        
        return joinOrder;
    }
    
    private Map<String, String> generateContextualHints(VectorContext vectorContext, GraphContext graphContext, String query) {
        Map<String, String> hints = new HashMap<>();
        
        // Add hints based on query analysis
        if (query.toLowerCase().contains("user")) {
            hints.put("focus", "User-related tables have high relevance");
        }
        
        if (query.toLowerCase().contains("order") || query.toLowerCase().contains("purchase")) {
            hints.put("relationships", "Consider order-customer-product relationships");
        }
        
        if (vectorContext.isSuccess() && !vectorContext.getRelevantTables().isEmpty()) {
            ContextProvider.TableContext mostRelevant = vectorContext.getRelevantTables().get(0);
            hints.put("primary_table", "Start with " + mostRelevant.getTableName() + " (relevance: " + 
                String.format("%.2f", mostRelevant.getRelevanceScore()) + ")");
        }
        
        if (graphContext.isSuccess() && !graphContext.getTableRelationships().isEmpty()) {
            hints.put("relationships_available", "Graph relationships found for " + 
                graphContext.getTableRelationships().size() + " tables");
        }
        
        return hints;
    }
}

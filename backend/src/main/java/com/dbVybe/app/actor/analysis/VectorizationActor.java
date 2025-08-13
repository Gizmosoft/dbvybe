package com.dbVybe.app.actor.analysis;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.service.agent.VectorAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VectorizationActor - Handles vector-based semantic analysis of database schemas
 * 
 * This actor:
 * - Performs semantic search over vectorized schema data
 * - Provides contextual information for query generation
 * - Analyzes schema patterns and relationships using vector similarity
 * - Integrates with QueryGenerationActor and NLPAgent for enhanced context
 */
public class VectorizationActor extends AbstractBehavior<VectorizationActor.Command> {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorizationActor.class);
    
    private final VectorAnalysisAgent vectorAnalysisAgent;
    private final AtomicLong activeRequests = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    
    // Actor Commands
    public interface Command {}
    
    /**
     * Perform semantic search for schema elements
     */
    public static class PerformSemanticSearch implements Command {
        private final String query;
        private final String userId;
        private final String connectionId;
        private final int limit;
        private final ActorRef<SemanticSearchResponse> replyTo;
        
        public PerformSemanticSearch(String query, String userId, String connectionId, int limit, 
                                   ActorRef<SemanticSearchResponse> replyTo) {
            this.query = query;
            this.userId = userId;
            this.connectionId = connectionId;
            this.limit = limit;
            this.replyTo = replyTo;
        }
        
        public String getQuery() { return query; }
        public String getUserId() { return userId; }
        public String getConnectionId() { return connectionId; }
        public int getLimit() { return limit; }
        public ActorRef<SemanticSearchResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Find related tables using vector similarity
     */
    public static class FindRelatedTables implements Command {
        private final String tableName;
        private final String connectionId;
        private final int limit;
        private final ActorRef<RelatedTablesResponse> replyTo;
        
        public FindRelatedTables(String tableName, String connectionId, int limit, 
                               ActorRef<RelatedTablesResponse> replyTo) {
            this.tableName = tableName;
            this.connectionId = connectionId;
            this.limit = limit;
            this.replyTo = replyTo;
        }
        
        public String getTableName() { return tableName; }
        public String getConnectionId() { return connectionId; }
        public int getLimit() { return limit; }
        public ActorRef<RelatedTablesResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get contextual information for query generation
     */
    public static class GetQueryContext implements Command {
        private final String naturalLanguageQuery;
        private final String connectionId;
        private final String userId;
        private final ActorRef<QueryContextResponse> replyTo;
        
        public GetQueryContext(String naturalLanguageQuery, String connectionId, String userId, 
                             ActorRef<QueryContextResponse> replyTo) {
            this.naturalLanguageQuery = naturalLanguageQuery;
            this.connectionId = connectionId;
            this.userId = userId;
            this.replyTo = replyTo;
        }
        
        public String getNaturalLanguageQuery() { return naturalLanguageQuery; }
        public String getConnectionId() { return connectionId; }
        public String getUserId() { return userId; }
        public ActorRef<QueryContextResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Analyze schema patterns across databases
     */
    public static class AnalyzeSchemaPatterns implements Command {
        private final String userId;
        private final List<String> connectionIds;
        private final ActorRef<SchemaPatternsResponse> replyTo;
        
        public AnalyzeSchemaPatterns(String userId, List<String> connectionIds, 
                                   ActorRef<SchemaPatternsResponse> replyTo) {
            this.userId = userId;
            this.connectionIds = connectionIds;
            this.replyTo = replyTo;
        }
        
        public String getUserId() { return userId; }
        public List<String> getConnectionIds() { return connectionIds; }
        public ActorRef<SchemaPatternsResponse> getReplyTo() { return replyTo; }
    }
    
    /**
     * Get vectorization statistics
     */
    public static class GetVectorizationStats implements Command {
        private final ActorRef<VectorizationStatsResponse> replyTo;
        
        public GetVectorizationStats(ActorRef<VectorizationStatsResponse> replyTo) {
            this.replyTo = replyTo;
        }
        
        public ActorRef<VectorizationStatsResponse> getReplyTo() { return replyTo; }
    }
    
    // Response Classes
    
    public static class SemanticSearchResponse {
        private final List<VectorAnalysisAgent.SemanticMatch> matches;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public SemanticSearchResponse(List<VectorAnalysisAgent.SemanticMatch> matches, boolean success, 
                                    String error, String requestId) {
            this.matches = matches;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public List<VectorAnalysisAgent.SemanticMatch> getMatches() { return matches; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class RelatedTablesResponse {
        private final List<VectorAnalysisAgent.RelatedTable> relatedTables;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public RelatedTablesResponse(List<VectorAnalysisAgent.RelatedTable> relatedTables, boolean success, 
                                   String error, String requestId) {
            this.relatedTables = relatedTables;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public List<VectorAnalysisAgent.RelatedTable> getRelatedTables() { return relatedTables; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class QueryContextResponse {
        private final List<VectorAnalysisAgent.TableContext> tableContexts;
        private final List<String> suggestedTables;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public QueryContextResponse(List<VectorAnalysisAgent.TableContext> tableContexts, 
                                  List<String> suggestedTables, boolean success, 
                                  String error, String requestId) {
            this.tableContexts = tableContexts;
            this.suggestedTables = suggestedTables;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public List<VectorAnalysisAgent.TableContext> getTableContexts() { return tableContexts; }
        public List<String> getSuggestedTables() { return suggestedTables; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class SchemaPatternsResponse {
        private final java.util.Map<String, List<String>> patterns;
        private final boolean success;
        private final String error;
        private final String requestId;
        
        public SchemaPatternsResponse(java.util.Map<String, List<String>> patterns, boolean success, 
                                    String error, String requestId) {
            this.patterns = patterns;
            this.success = success;
            this.error = error;
            this.requestId = requestId;
        }
        
        public java.util.Map<String, List<String>> getPatterns() { return patterns; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getRequestId() { return requestId; }
    }
    
    public static class VectorizationStatsResponse {
        private final long activeRequests;
        private final long totalRequests;
        private final VectorAnalysisAgent.VectorAnalysisStats agentStats;
        private final boolean success;
        
        public VectorizationStatsResponse(long activeRequests, long totalRequests, 
                                        VectorAnalysisAgent.VectorAnalysisStats agentStats, boolean success) {
            this.activeRequests = activeRequests;
            this.totalRequests = totalRequests;
            this.agentStats = agentStats;
            this.success = success;
        }
        
        public long getActiveRequests() { return activeRequests; }
        public long getTotalRequests() { return totalRequests; }
        public VectorAnalysisAgent.VectorAnalysisStats getAgentStats() { return agentStats; }
        public boolean isSuccess() { return success; }
    }
    
    public static Behavior<Command> create(VectorAnalysisAgent vectorAnalysisAgent) {
        return Behaviors.setup(context -> new VectorizationActor(context, vectorAnalysisAgent));
    }
    
    private VectorizationActor(ActorContext<Command> context, VectorAnalysisAgent vectorAnalysisAgent) {
        super(context);
        this.vectorAnalysisAgent = vectorAnalysisAgent;
        logger.info("VectorizationActor created and ready for semantic analysis operations");
    }
    
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(PerformSemanticSearch.class, this::onPerformSemanticSearch)
            .onMessage(FindRelatedTables.class, this::onFindRelatedTables)
            .onMessage(GetQueryContext.class, this::onGetQueryContext)
            .onMessage(AnalyzeSchemaPatterns.class, this::onAnalyzeSchemaPatterns)
            .onMessage(GetVectorizationStats.class, this::onGetVectorizationStats)
            .build();
    }
    
    private Behavior<Command> onPerformSemanticSearch(PerformSemanticSearch command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Performing semantic search for query: '{}' (user: {}, connection: {}) - Request ID: {}", 
            command.getQuery(), command.getUserId(), command.getConnectionId(), requestId);
        
        vectorAnalysisAgent.semanticSchemaSearch(command.getQuery(), command.getUserId(), command.getLimit())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Semantic search failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new SemanticSearchResponse(
                            java.util.Collections.emptyList(), false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Semantic search completed successfully (Request ID: {}) - Found {} matches", 
                                requestId, result.getTotalResults());
                            command.getReplyTo().tell(new SemanticSearchResponse(
                                result.getMatches(), true, null, requestId));
                        } else {
                            logger.error("Semantic search failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new SemanticSearchResponse(
                                java.util.Collections.emptyList(), false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in semantic search completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onFindRelatedTables(FindRelatedTables command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Finding related tables for: {} in connection: {} - Request ID: {}", 
            command.getTableName(), command.getConnectionId(), requestId);
        
        vectorAnalysisAgent.findRelatedTables(command.getTableName(), command.getConnectionId(), command.getLimit())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Find related tables failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new RelatedTablesResponse(
                            java.util.Collections.emptyList(), false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Found {} related tables (Request ID: {})", result.getRelatedTables().size(), requestId);
                            command.getReplyTo().tell(new RelatedTablesResponse(
                                result.getRelatedTables(), true, null, requestId));
                        } else {
                            logger.error("Find related tables failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new RelatedTablesResponse(
                                java.util.Collections.emptyList(), false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in find related tables completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetQueryContext(GetQueryContext command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Getting query context for: '{}' (connection: {}, user: {}) - Request ID: {}", 
            command.getNaturalLanguageQuery(), command.getConnectionId(), command.getUserId(), requestId);
        
        vectorAnalysisAgent.getQueryContext(command.getNaturalLanguageQuery(), command.getConnectionId(), command.getUserId())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Get query context failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new QueryContextResponse(
                            java.util.Collections.emptyList(), java.util.Collections.emptyList(), 
                            false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Generated query context with {} table contexts (Request ID: {})", 
                                result.getTableContexts().size(), requestId);
                            command.getReplyTo().tell(new QueryContextResponse(
                                result.getTableContexts(), result.getSuggestedTables(), true, null, requestId));
                        } else {
                            logger.error("Get query context failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new QueryContextResponse(
                                java.util.Collections.emptyList(), java.util.Collections.emptyList(), 
                                false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in get query context completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onAnalyzeSchemaPatterns(AnalyzeSchemaPatterns command) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
        
        logger.info("Analyzing schema patterns for user: {} across {} connections - Request ID: {}", 
            command.getUserId(), command.getConnectionIds().size(), requestId);
        
        vectorAnalysisAgent.analyzeSchemaPatterns(command.getUserId(), command.getConnectionIds())
            .whenComplete((result, throwable) -> {
                try {
                    activeRequests.decrementAndGet();
                    
                    if (throwable != null) {
                        logger.error("Schema pattern analysis failed (Request ID: {}): {}", requestId, throwable.getMessage());
                        command.getReplyTo().tell(new SchemaPatternsResponse(
                            java.util.Collections.emptyMap(), false, throwable.getMessage(), requestId));
                    } else {
                        if (result.isSuccess()) {
                            logger.info("Schema pattern analysis completed with {} patterns (Request ID: {})", 
                                result.getPatterns().size(), requestId);
                            command.getReplyTo().tell(new SchemaPatternsResponse(
                                result.getPatterns(), true, null, requestId));
                        } else {
                            logger.error("Schema pattern analysis failed (Request ID: {}): {}", requestId, result.getError());
                            command.getReplyTo().tell(new SchemaPatternsResponse(
                                java.util.Collections.emptyMap(), false, result.getError(), requestId));
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in schema pattern analysis completion handler (Request ID: {}): {}", requestId, e.getMessage());
                }
            });
        
        return Behaviors.same();
    }
    
    private Behavior<Command> onGetVectorizationStats(GetVectorizationStats command) {
        logger.debug("Providing vectorization statistics");
        
        try {
            VectorAnalysisAgent.VectorAnalysisStats agentStats = vectorAnalysisAgent.getStats();
            
            command.getReplyTo().tell(new VectorizationStatsResponse(
                activeRequests.get(),
                totalRequests.get(),
                agentStats,
                true
            ));
        } catch (Exception e) {
            logger.error("Error getting vectorization stats: {}", e.getMessage(), e);
            command.getReplyTo().tell(new VectorizationStatsResponse(0, 0, null, false));
        }
        
        return Behaviors.same();
    }
}

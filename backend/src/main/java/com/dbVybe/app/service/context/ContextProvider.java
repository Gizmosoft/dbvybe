package com.dbVybe.app.service.context;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Context Provider Interface - Provides contextual information for query generation and NLP processing
 * 
 * This interface defines methods for obtaining various types of context information
 * that can enhance the LLM's understanding of database structures and relationships.
 */
public interface ContextProvider {
    
    /**
     * Get vector-based semantic context for a natural language query
     */
    CompletableFuture<VectorContext> getVectorContext(String naturalLanguageQuery, String connectionId, String userId);
    
    /**
     * Get graph-based relationship context for tables
     */
    CompletableFuture<GraphContext> getGraphContext(String connectionId, List<String> tableNames, int maxDepth);
    
    /**
     * Get combined context (vector + graph) for comprehensive query understanding
     */
    CompletableFuture<CombinedContext> getCombinedContext(String naturalLanguageQuery, String connectionId, String userId);
    
    /**
     * Get schema analysis context for a connection
     */
    CompletableFuture<SchemaContext> getSchemaContext(String connectionId, String userId);
    
    /**
     * Vector-based context information
     */
    class VectorContext {
        private final List<TableContext> relevantTables;
        private final List<String> suggestedTables;
        private final Map<String, Float> tableRelevanceScores;
        private final boolean success;
        private final String error;
        
        public VectorContext(List<TableContext> relevantTables, List<String> suggestedTables,
                           Map<String, Float> tableRelevanceScores, boolean success, String error) {
            this.relevantTables = relevantTables;
            this.suggestedTables = suggestedTables;
            this.tableRelevanceScores = tableRelevanceScores;
            this.success = success;
            this.error = error;
        }
        
        public List<TableContext> getRelevantTables() { return relevantTables; }
        public List<String> getSuggestedTables() { return suggestedTables; }
        public Map<String, Float> getTableRelevanceScores() { return tableRelevanceScores; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    /**
     * Graph-based context information
     */
    class GraphContext {
        private final Map<String, List<String>> tableRelationships;
        private final List<RelationshipPath> relationshipPaths;
        private final Map<String, Integer> dependencyCounts;
        private final boolean success;
        private final String error;
        
        public GraphContext(Map<String, List<String>> tableRelationships, List<RelationshipPath> relationshipPaths,
                          Map<String, Integer> dependencyCounts, boolean success, String error) {
            this.tableRelationships = tableRelationships;
            this.relationshipPaths = relationshipPaths;
            this.dependencyCounts = dependencyCounts;
            this.success = success;
            this.error = error;
        }
        
        public Map<String, List<String>> getTableRelationships() { return tableRelationships; }
        public List<RelationshipPath> getRelationshipPaths() { return relationshipPaths; }
        public Map<String, Integer> getDependencyCounts() { return dependencyCounts; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    /**
     * Combined context information
     */
    class CombinedContext {
        private final VectorContext vectorContext;
        private final GraphContext graphContext;
        private final List<String> recommendedJoinOrder;
        private final Map<String, String> contextualHints;
        private final boolean success;
        private final String error;
        
        public CombinedContext(VectorContext vectorContext, GraphContext graphContext,
                             List<String> recommendedJoinOrder, Map<String, String> contextualHints,
                             boolean success, String error) {
            this.vectorContext = vectorContext;
            this.graphContext = graphContext;
            this.recommendedJoinOrder = recommendedJoinOrder;
            this.contextualHints = contextualHints;
            this.success = success;
            this.error = error;
        }
        
        public VectorContext getVectorContext() { return vectorContext; }
        public GraphContext getGraphContext() { return graphContext; }
        public List<String> getRecommendedJoinOrder() { return recommendedJoinOrder; }
        public Map<String, String> getContextualHints() { return contextualHints; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    /**
     * Schema analysis context
     */
    class SchemaContext {
        private final Map<String, TableSchema> tableSchemas;
        private final List<String> availableTables;
        private final Map<String, List<String>> foreignKeyRelationships;
        private final boolean success;
        private final String error;
        
        public SchemaContext(Map<String, TableSchema> tableSchemas, List<String> availableTables,
                           Map<String, List<String>> foreignKeyRelationships, boolean success, String error) {
            this.tableSchemas = tableSchemas;
            this.availableTables = availableTables;
            this.foreignKeyRelationships = foreignKeyRelationships;
            this.success = success;
            this.error = error;
        }
        
        public Map<String, TableSchema> getTableSchemas() { return tableSchemas; }
        public List<String> getAvailableTables() { return availableTables; }
        public Map<String, List<String>> getForeignKeyRelationships() { return foreignKeyRelationships; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    // Supporting data classes
    
    class TableContext {
        private final String tableName;
        private final String description;
        private final List<String> columns;
        private final Map<String, String> columnTypes;
        private final float relevanceScore;
        
        public TableContext(String tableName, String description, List<String> columns,
                          Map<String, String> columnTypes, float relevanceScore) {
            this.tableName = tableName;
            this.description = description;
            this.columns = columns;
            this.columnTypes = columnTypes;
            this.relevanceScore = relevanceScore;
        }
        
        public String getTableName() { return tableName; }
        public String getDescription() { return description; }
        public List<String> getColumns() { return columns; }
        public Map<String, String> getColumnTypes() { return columnTypes; }
        public float getRelevanceScore() { return relevanceScore; }
    }
    
    class RelationshipPath {
        private final String sourceTable;
        private final String targetTable;
        private final List<String> pathTables;
        private final List<String> relationshipTypes;
        private final int pathLength;
        
        public RelationshipPath(String sourceTable, String targetTable, List<String> pathTables,
                              List<String> relationshipTypes, int pathLength) {
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
            this.pathTables = pathTables;
            this.relationshipTypes = relationshipTypes;
            this.pathLength = pathLength;
        }
        
        public String getSourceTable() { return sourceTable; }
        public String getTargetTable() { return targetTable; }
        public List<String> getPathTables() { return pathTables; }
        public List<String> getRelationshipTypes() { return relationshipTypes; }
        public int getPathLength() { return pathLength; }
    }
    
    class TableSchema {
        private final String tableName;
        private final List<ColumnSchema> columns;
        private final List<String> primaryKeys;
        private final List<ForeignKeySchema> foreignKeys;
        
        public TableSchema(String tableName, List<ColumnSchema> columns, List<String> primaryKeys,
                         List<ForeignKeySchema> foreignKeys) {
            this.tableName = tableName;
            this.columns = columns;
            this.primaryKeys = primaryKeys;
            this.foreignKeys = foreignKeys;
        }
        
        public String getTableName() { return tableName; }
        public List<ColumnSchema> getColumns() { return columns; }
        public List<String> getPrimaryKeys() { return primaryKeys; }
        public List<ForeignKeySchema> getForeignKeys() { return foreignKeys; }
    }
    
    class ColumnSchema {
        private final String columnName;
        private final String dataType;
        private final boolean nullable;
        private final String defaultValue;
        private final String comment;
        
        public ColumnSchema(String columnName, String dataType, boolean nullable,
                          String defaultValue, String comment) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.comment = comment;
        }
        
        public String getColumnName() { return columnName; }
        public String getDataType() { return dataType; }
        public boolean isNullable() { return nullable; }
        public String getDefaultValue() { return defaultValue; }
        public String getComment() { return comment; }
    }
    
    class ForeignKeySchema {
        private final String columnName;
        private final String referencedTable;
        private final String referencedColumn;
        
        public ForeignKeySchema(String columnName, String referencedTable, String referencedColumn) {
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
        }
        
        public String getColumnName() { return columnName; }
        public String getReferencedTable() { return referencedTable; }
        public String getReferencedColumn() { return referencedColumn; }
    }
}

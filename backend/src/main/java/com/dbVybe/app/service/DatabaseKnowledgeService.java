package com.dbVybe.app.service;

import com.dbVybe.app.domain.model.DatabaseType;
import com.dbVybe.app.domain.model.UserDatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database Knowledge Service
 * 
 * This service:
 * 1. Stores complete database information when a connection is established
 * 2. Provides intelligent responses based on stored knowledge
 * 3. Decides whether to answer from knowledge or run a query
 * 4. Generates clean, simple responses without markdown formatting
 */
@Service
public class DatabaseKnowledgeService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseKnowledgeService.class);
    
    // In-memory storage of database knowledge
    private final Map<String, DatabaseKnowledge> databaseKnowledgeMap = new ConcurrentHashMap<>();
    
    /**
     * Store complete database knowledge when connection is established
     */
    public void storeDatabaseKnowledge(UserDatabaseConnection connection) {
        try {
            logger.info("Storing complete database knowledge for connection: {} ({}:{}/{})", 
                connection.getConnectionId(), connection.getHost(), connection.getPort(), connection.getDatabaseName());
            
            DatabaseKnowledge knowledge = new DatabaseKnowledge();
            knowledge.setConnectionId(connection.getConnectionId());
            knowledge.setDatabaseName(connection.getDatabaseName());
            knowledge.setDatabaseType(DatabaseType.fromString(connection.getDatabaseType()));
            knowledge.setUserId(connection.getUserId());
            
            // Extract complete database structure
            extractCompleteDatabaseStructure(connection, knowledge);
            
            // Store in memory
            databaseKnowledgeMap.put(connection.getConnectionId(), knowledge);
            
            logger.info("Stored complete database knowledge for {}: {} tables, {} columns", 
                connection.getDatabaseName(), knowledge.getTables().size(), knowledge.getTotalColumns());
            
        } catch (Exception e) {
            logger.error("Error storing database knowledge: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extract complete database structure
     */
    private void extractCompleteDatabaseStructure(UserDatabaseConnection connection, DatabaseKnowledge knowledge) {
        try (Connection dbConnection = getDatabaseConnection(connection)) {
            DatabaseMetaData metaData = dbConnection.getMetaData();
            
            // Get database name
            String databaseName = dbConnection.getCatalog();
            if (databaseName == null) {
                databaseName = dbConnection.getSchema();
            }
            knowledge.setDatabaseName(databaseName);
            
            // Get all schemas
            Set<String> schemas = new HashSet<>();
            try (ResultSet schemaRs = metaData.getSchemas()) {
                while (schemaRs.next()) {
                    schemas.add(schemaRs.getString("TABLE_SCHEMA"));
                }
            }
            knowledge.setSchemas(schemas);
            
            // Get all tables from all schemas
            List<TableKnowledge> tables = new ArrayList<>();
            for (String schema : schemas) {
                try (ResultSet tablesRs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
                    while (tablesRs.next()) {
                        String tableName = tablesRs.getString("TABLE_NAME");
                        String tableComment = tablesRs.getString("REMARKS");
                        
                        TableKnowledge tableKnowledge = new TableKnowledge();
                        tableKnowledge.setSchema(schema);
                        tableKnowledge.setName(tableName);
                        tableKnowledge.setComment(tableComment);
                        
                        // Get columns
                        List<ColumnKnowledge> columns = getTableColumns(metaData, schema, tableName);
                        tableKnowledge.setColumns(columns);
                        
                        // Get primary keys
                        Set<String> primaryKeys = getPrimaryKeys(metaData, schema, tableName);
                        tableKnowledge.setPrimaryKeys(primaryKeys);
                        
                        // Get foreign keys
                        List<ForeignKeyKnowledge> foreignKeys = getForeignKeys(metaData, schema, tableName);
                        tableKnowledge.setForeignKeys(foreignKeys);
                        
                        // Get indexes
                        List<IndexKnowledge> indexes = getIndexes(metaData, schema, tableName);
                        tableKnowledge.setIndexes(indexes);
                        
                        tables.add(tableKnowledge);
                    }
                }
            }
            knowledge.setTables(tables);
            
            // Calculate total columns
            int totalColumns = tables.stream().mapToInt(t -> t.getColumns().size()).sum();
            knowledge.setTotalColumns(totalColumns);
            
        } catch (Exception e) {
            logger.error("Error extracting database structure: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get table columns
     */
    private List<ColumnKnowledge> getTableColumns(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        List<ColumnKnowledge> columns = new ArrayList<>();
        
        try (ResultSet columnsRs = metaData.getColumns(null, schema, tableName, "%")) {
            while (columnsRs.next()) {
                ColumnKnowledge column = new ColumnKnowledge();
                column.setName(columnsRs.getString("COLUMN_NAME"));
                column.setDataType(columnsRs.getString("TYPE_NAME"));
                column.setSize(columnsRs.getInt("COLUMN_SIZE"));
                column.setNullable("YES".equals(columnsRs.getString("IS_NULLABLE")));
                column.setDefaultValue(columnsRs.getString("COLUMN_DEF"));
                column.setComment(columnsRs.getString("REMARKS"));
                column.setOrdinalPosition(columnsRs.getInt("ORDINAL_POSITION"));
                
                columns.add(column);
            }
        }
        
        return columns;
    }
    
    /**
     * Get primary keys
     */
    private Set<String> getPrimaryKeys(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        
        try (ResultSet pkRs = metaData.getPrimaryKeys(null, schema, tableName)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }
        
        return primaryKeys;
    }
    
    /**
     * Get foreign keys
     */
    private List<ForeignKeyKnowledge> getForeignKeys(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        List<ForeignKeyKnowledge> foreignKeys = new ArrayList<>();
        
        try (ResultSet fkRs = metaData.getImportedKeys(null, schema, tableName)) {
            while (fkRs.next()) {
                ForeignKeyKnowledge fk = new ForeignKeyKnowledge();
                fk.setColumnName(fkRs.getString("FKCOLUMN_NAME"));
                fk.setReferencedTable(fkRs.getString("PKTABLE_NAME"));
                fk.setReferencedColumn(fkRs.getString("PKCOLUMN_NAME"));
                fk.setReferencedSchema(fkRs.getString("PKTABLE_SCHEM"));
                
                foreignKeys.add(fk);
            }
        }
        
        return foreignKeys;
    }
    
    /**
     * Get indexes
     */
    private List<IndexKnowledge> getIndexes(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        List<IndexKnowledge> indexes = new ArrayList<>();
        
        try (ResultSet indexRs = metaData.getIndexInfo(null, schema, tableName, false, false)) {
            while (indexRs.next()) {
                IndexKnowledge index = new IndexKnowledge();
                index.setName(indexRs.getString("INDEX_NAME"));
                index.setColumnName(indexRs.getString("COLUMN_NAME"));
                index.setUnique(!indexRs.getBoolean("NON_UNIQUE"));
                
                indexes.add(index);
            }
        }
        
        return indexes;
    }
    
    /**
     * Get database connection
     */
    private Connection getDatabaseConnection(UserDatabaseConnection connection) throws SQLException {
        try {
            String databaseType = connection.getDatabaseType();
            String host = connection.getHost();
            int port = connection.getPort();
            String databaseName = connection.getDatabaseName();
            String username = connection.getUsername();
            String password = connection.getPassword();
            
            logger.info("Creating database connection for {}:{}:{}/{}", host, port, databaseName, databaseType);
            
            switch (DatabaseType.fromString(databaseType)) {
                case POSTGRESQL:
                    return java.sql.DriverManager.getConnection(
                        String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName),
                        username, password
                    );
                case MYSQL:
                    return java.sql.DriverManager.getConnection(
                        String.format("jdbc:mysql://%s:%d/%s", host, port, databaseName),
                        username, password
                    );
                case MONGODB:
                    // MongoDB doesn't use JDBC, so we'll skip it for now
                    logger.warn("MongoDB connection not implemented for schema extraction");
                    return null;
                default:
                    logger.warn("Unsupported database type: {}", databaseType);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Error creating database connection: {}", e.getMessage(), e);
            throw new SQLException("Failed to create database connection: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get database knowledge for a connection
     */
    public DatabaseKnowledge getDatabaseKnowledge(String connectionId) {
        return databaseKnowledgeMap.get(connectionId);
    }
    
    /**
     * Check if we can answer a question from stored knowledge
     */
    public boolean canAnswerFromKnowledge(String question, String connectionId) {
        DatabaseKnowledge knowledge = getDatabaseKnowledge(connectionId);
        logger.info("Checking if can answer from knowledge for connectionId: {}, knowledge exists: {}", 
            connectionId, knowledge != null);
        
        if (knowledge == null) {
            logger.warn("No knowledge found for connectionId: {}", connectionId);
            return false;
        }
        
        String lowerQuestion = question.toLowerCase();
        
        // Questions we can answer from knowledge
        if (lowerQuestion.contains("table") || lowerQuestion.contains("tables")) {
            return true;
        }
        
        if (lowerQuestion.contains("column") || lowerQuestion.contains("columns") || lowerQuestion.contains("structure")) {
            return true;
        }
        
        if (lowerQuestion.contains("relationship") || lowerQuestion.contains("foreign key") || lowerQuestion.contains("primary key")) {
            return true;
        }
        
        if (lowerQuestion.contains("database name") || lowerQuestion.contains("what is this database")) {
            return true;
        }
        
        if (lowerQuestion.contains("schema") || lowerQuestion.contains("schemas")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Generate response from stored knowledge
     */
    public String generateKnowledgeResponse(String question, String connectionId) {
        DatabaseKnowledge knowledge = getDatabaseKnowledge(connectionId);
        if (knowledge == null) {
            return "I don't have information about this database connection.";
        }
        
        String lowerQuestion = question.toLowerCase();
        StringBuilder response = new StringBuilder();
        
        if (lowerQuestion.contains("database name") || lowerQuestion.contains("what is this database")) {
            response.append("This database is named: ").append(knowledge.getDatabaseName()).append("\n");
            response.append("Database type: ").append(knowledge.getDatabaseType()).append("\n");
        }
        
        if (lowerQuestion.contains("table") || lowerQuestion.contains("tables")) {
            response.append("This database contains ").append(knowledge.getTables().size()).append(" tables:\n");
            for (TableKnowledge table : knowledge.getTables()) {
                response.append("- ").append(table.getSchema()).append(".").append(table.getName());
                if (table.getComment() != null && !table.getComment().isEmpty()) {
                    response.append(" (").append(table.getComment()).append(")");
                }
                response.append("\n");
            }
        }
        
        if (lowerQuestion.contains("column") || lowerQuestion.contains("columns") || lowerQuestion.contains("structure")) {
            String tableName = extractTableName(question);
            if (tableName != null) {
                TableKnowledge table = findTable(knowledge, tableName);
                if (table != null) {
                    response.append("Table ").append(table.getSchema()).append(".").append(table.getName()).append(" has ").append(table.getColumns().size()).append(" columns:\n");
                    for (ColumnKnowledge column : table.getColumns()) {
                        response.append("- ").append(column.getName()).append(" (").append(column.getDataType());
                        if (column.getSize() > 0) {
                            response.append("(").append(column.getSize()).append(")");
                        }
                        response.append(")");
                        if (!column.isNullable()) {
                            response.append(" NOT NULL");
                        }
                        if (column.getComment() != null && !column.getComment().isEmpty()) {
                            response.append(" - ").append(column.getComment());
                        }
                        response.append("\n");
                    }
                } else {
                    response.append("Table '").append(tableName).append("' not found in this database.\n");
                }
            } else {
                response.append("Please specify which table you want to know about.\n");
            }
        }
        
        if (lowerQuestion.contains("relationship") || lowerQuestion.contains("foreign key")) {
            response.append("Database relationships:\n");
            for (TableKnowledge table : knowledge.getTables()) {
                if (!table.getForeignKeys().isEmpty()) {
                    response.append("Table ").append(table.getSchema()).append(".").append(table.getName()).append(" references:\n");
                    for (ForeignKeyKnowledge fk : table.getForeignKeys()) {
                        response.append("- ").append(fk.getColumnName()).append(" -> ").append(fk.getReferencedSchema()).append(".").append(fk.getReferencedTable()).append(".").append(fk.getReferencedColumn()).append("\n");
                    }
                }
            }
        }
        
        if (response.length() == 0) {
            response.append("I can answer questions about tables, columns, relationships, and database structure. Please ask a specific question.");
        }
        
        return response.toString().trim();
    }
    
    /**
     * Extract table name from question
     */
    private String extractTableName(String question) {
        // Simple extraction - could be improved with NLP
        String[] words = question.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].equalsIgnoreCase("table") && i + 1 < words.length) {
                return words[i + 1].replaceAll("[^a-zA-Z0-9_]", "");
            }
        }
        return null;
    }
    
    /**
     * Find table by name (case insensitive)
     */
    private TableKnowledge findTable(DatabaseKnowledge knowledge, String tableName) {
        return knowledge.getTables().stream()
            .filter(t -> t.getName().equalsIgnoreCase(tableName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Remove database knowledge when connection is closed
     */
    public void removeDatabaseKnowledge(String connectionId) {
        databaseKnowledgeMap.remove(connectionId);
        logger.info("Removed database knowledge for connection: {}", connectionId);
    }
    
    // Data classes for storing database knowledge
    public static class DatabaseKnowledge {
        private String connectionId;
        private String databaseName;
        private DatabaseType databaseType;
        private String userId;
        private Set<String> schemas = new HashSet<>();
        private List<TableKnowledge> tables = new ArrayList<>();
        private int totalColumns;
        
        // Getters and setters
        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
        
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        
        public DatabaseType getDatabaseType() { return databaseType; }
        public void setDatabaseType(DatabaseType databaseType) { this.databaseType = databaseType; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Set<String> getSchemas() { return schemas; }
        public void setSchemas(Set<String> schemas) { this.schemas = schemas; }
        
        public List<TableKnowledge> getTables() { return tables; }
        public void setTables(List<TableKnowledge> tables) { this.tables = tables; }
        
        public int getTotalColumns() { return totalColumns; }
        public void setTotalColumns(int totalColumns) { this.totalColumns = totalColumns; }
    }
    
    public static class TableKnowledge {
        private String schema;
        private String name;
        private String comment;
        private List<ColumnKnowledge> columns = new ArrayList<>();
        private Set<String> primaryKeys = new HashSet<>();
        private List<ForeignKeyKnowledge> foreignKeys = new ArrayList<>();
        private List<IndexKnowledge> indexes = new ArrayList<>();
        
        // Getters and setters
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        
        public List<ColumnKnowledge> getColumns() { return columns; }
        public void setColumns(List<ColumnKnowledge> columns) { this.columns = columns; }
        
        public Set<String> getPrimaryKeys() { return primaryKeys; }
        public void setPrimaryKeys(Set<String> primaryKeys) { this.primaryKeys = primaryKeys; }
        
        public List<ForeignKeyKnowledge> getForeignKeys() { return foreignKeys; }
        public void setForeignKeys(List<ForeignKeyKnowledge> foreignKeys) { this.foreignKeys = foreignKeys; }
        
        public List<IndexKnowledge> getIndexes() { return indexes; }
        public void setIndexes(List<IndexKnowledge> indexes) { this.indexes = indexes; }
    }
    
    public static class ColumnKnowledge {
        private String name;
        private String dataType;
        private int size;
        private boolean nullable;
        private String defaultValue;
        private String comment;
        private int ordinalPosition;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        
        public int getOrdinalPosition() { return ordinalPosition; }
        public void setOrdinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; }
    }
    
    public static class ForeignKeyKnowledge {
        private String columnName;
        private String referencedTable;
        private String referencedColumn;
        private String referencedSchema;
        
        // Getters and setters
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        
        public String getReferencedTable() { return referencedTable; }
        public void setReferencedTable(String referencedTable) { this.referencedTable = referencedTable; }
        
        public String getReferencedColumn() { return referencedColumn; }
        public void setReferencedColumn(String referencedColumn) { this.referencedColumn = referencedColumn; }
        
        public String getReferencedSchema() { return referencedSchema; }
        public void setReferencedSchema(String referencedSchema) { this.referencedSchema = referencedSchema; }
    }
    
    public static class IndexKnowledge {
        private String name;
        private String columnName;
        private boolean unique;
        
        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        
        public boolean isUnique() { return unique; }
        public void setUnique(boolean unique) { this.unique = unique; }
    }
}

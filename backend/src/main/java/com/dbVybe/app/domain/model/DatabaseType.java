package com.dbVybe.app.domain.model;

/**
 * Supported database types
 */
public enum DatabaseType {
    POSTGRESQL("postgresql"),
    MYSQL("mysql"),
    MONGODB("mongodb");
    
    private final String value;
    
    DatabaseType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static DatabaseType fromString(String text) {
        for (DatabaseType type : DatabaseType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown database type: " + text);
    }
}
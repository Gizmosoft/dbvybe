package com.dbVybe.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Database configuration properties loaded from application.properties
 * Maps to docker-compose.yml database settings
 */
@Configuration
@ConfigurationProperties(prefix = "db.mysql")
public class DatabaseConfig {
    
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String rootPassword;
    private String url;
    private String productionUrl;
    private String driver;
    
    // Connection pool settings
    private ConnectionPool connectionPool;
    
    // Health check settings
    private HealthCheck healthCheck;
    
    public static class ConnectionPool {
        private int initialSize;
        private int maxSize;
        private int minIdle;
        private long maxIdleTime;
        
        // Getters and Setters
        public int getInitialSize() { return initialSize; }
        public void setInitialSize(int initialSize) { this.initialSize = initialSize; }
        
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        
        public long getMaxIdleTime() { return maxIdleTime; }
        public void setMaxIdleTime(long maxIdleTime) { this.maxIdleTime = maxIdleTime; }
    }
    
    public static class HealthCheck {
        private String timeout;
        private int retries;
        
        // Getters and Setters
        public String getTimeout() { return timeout; }
        public void setTimeout(String timeout) { this.timeout = timeout; }
        
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
    }
    
    // Getters and Setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getRootPassword() { return rootPassword; }
    public void setRootPassword(String rootPassword) { this.rootPassword = rootPassword; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getProductionUrl() { return productionUrl; }
    public void setProductionUrl(String productionUrl) { this.productionUrl = productionUrl; }
    
    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }
    
    public ConnectionPool getConnectionPool() { return connectionPool; }
    public void setConnectionPool(ConnectionPool connectionPool) { this.connectionPool = connectionPool; }
    
    public HealthCheck getHealthCheck() { return healthCheck; }
    public void setHealthCheck(HealthCheck healthCheck) { this.healthCheck = healthCheck; }
    
    /**
     * Get the full database URL for the current environment
     */
    public String getDatabaseUrl() {
        return url.replace("${db.mysql.host}", host)
                 .replace("${db.mysql.port}", String.valueOf(port))
                 .replace("${db.mysql.database}", database);
    }
    
    /**
     * Get the production database URL
     */
    public String getProductionDatabaseUrl() {
        return productionUrl.replace("${db.mysql.host}", host)
                          .replace("${db.mysql.port}", String.valueOf(port))
                          .replace("${db.mysql.database}", database);
    }
    
    @Override
    public String toString() {
        return "DatabaseConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", driver='" + driver + '\'' +
                '}';
    }
} 
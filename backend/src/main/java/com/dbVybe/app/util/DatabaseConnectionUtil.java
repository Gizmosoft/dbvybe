package com.dbVybe.app.util;

import com.dbVybe.app.config.DatabaseConfig;
import com.dbVybe.app.config.DockerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class for database connection management using properties from application.properties
 */
@Component
public class DatabaseConnectionUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionUtil.class);
    
    private final DatabaseConfig databaseConfig;
    private final DockerConfig dockerConfig;
    
    @Autowired
    public DatabaseConnectionUtil(DatabaseConfig databaseConfig, DockerConfig dockerConfig) {
        this.databaseConfig = databaseConfig;
        this.dockerConfig = dockerConfig;
        logger.info("DatabaseConnectionUtil initialized with config: {}", databaseConfig);
    }
    
    /**
     * Test database connection using properties from application.properties
     */
    public boolean testDatabaseConnection() {
        try {
            String url = databaseConfig.getDatabaseUrl();
            String username = databaseConfig.getUsername();
            String password = databaseConfig.getPassword();
            
            logger.info("Testing database connection to: {}", url);
            
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                logger.info("Database connection successful!");
                return true;
            }
            
        } catch (SQLException e) {
            logger.error("Database connection failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get database connection using properties from application.properties
     */
    public Connection getDatabaseConnection() throws SQLException {
        String url = databaseConfig.getDatabaseUrl();
        String username = databaseConfig.getUsername();
        String password = databaseConfig.getPassword();
        
        logger.debug("Creating database connection to: {}", url);
        return DriverManager.getConnection(url, username, password);
    }
    
    /**
     * Get database configuration summary
     */
    public String getDatabaseConfigSummary() {
        return String.format(
            "Database Config: Host=%s, Port=%d, Database=%s, Username=%s, Driver=%s",
            databaseConfig.getHost(),
            databaseConfig.getPort(),
            databaseConfig.getDatabase(),
            databaseConfig.getUsername(),
            databaseConfig.getDriver()
        );
    }
    
    /**
     * Get Docker configuration summary
     */
    public String getDockerConfigSummary() {
        if (dockerConfig.getMysql() != null) {
            return String.format(
                "Docker Config: Container=%s, Image=%s, Network=%s",
                dockerConfig.getMysql().getContainerName(),
                dockerConfig.getMysql().getImage(),
                dockerConfig.getNetwork() != null ? dockerConfig.getNetwork().getName() : "N/A"
            );
        }
        return "Docker Config: Not available";
    }
    
    /**
     * Check if database is running in Docker
     */
    public boolean isDockerDatabase() {
        return "localhost".equals(databaseConfig.getHost()) && 
               databaseConfig.getPort() == 3306;
    }
    
    /**
     * Get connection pool configuration
     */
    public String getConnectionPoolConfig() {
        if (databaseConfig.getConnectionPool() != null) {
            return String.format(
                "Connection Pool: Initial=%d, Max=%d, MinIdle=%d, MaxIdleTime=%dms",
                databaseConfig.getConnectionPool().getInitialSize(),
                databaseConfig.getConnectionPool().getMaxSize(),
                databaseConfig.getConnectionPool().getMinIdle(),
                databaseConfig.getConnectionPool().getMaxIdleTime()
            );
        }
        return "Connection Pool: Not configured";
    }
    
    /**
     * Get health check configuration
     */
    public String getHealthCheckConfig() {
        if (databaseConfig.getHealthCheck() != null) {
            return String.format(
                "Health Check: Timeout=%s, Retries=%d",
                databaseConfig.getHealthCheck().getTimeout(),
                databaseConfig.getHealthCheck().getRetries()
            );
        }
        return "Health Check: Not configured";
    }
} 
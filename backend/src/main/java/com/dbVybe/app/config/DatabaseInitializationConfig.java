package com.dbVybe.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Configuration for database initialization
 * Handles graceful startup even if database is not available
 */
@Configuration
public class DatabaseInitializationConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializationConfig.class);
    
    /**
     * Database health check bean
     * Only created if database is enabled
     */
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.url")
    @DependsOn("dataSource")
    public DatabaseHealthCheck databaseHealthCheck(DataSource dataSource) {
        return new DatabaseHealthCheck(dataSource);
    }
    
    /**
     * Inner class for database health checking
     */
    public static class DatabaseHealthCheck {
        
        private final DataSource dataSource;
        
        public DatabaseHealthCheck(DataSource dataSource) {
            this.dataSource = dataSource;
            checkDatabaseConnection();
        }
        
        private void checkDatabaseConnection() {
            try (Connection connection = dataSource.getConnection()) {
                logger.info("✅ Database connection established successfully");
            } catch (SQLException e) {
                logger.warn("⚠️ Database connection failed during startup: {}", e.getMessage());
                logger.info("💡 The application will continue to start, but database features may not work");
                logger.info("💡 Make sure MySQL is running on localhost:3306 or update application.properties");
            }
        }
    }
}

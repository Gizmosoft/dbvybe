package com.dbVybe.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Docker configuration properties loaded from application.properties
 * Maps to docker-compose.yml settings
 */
@Configuration
@ConfigurationProperties(prefix = "docker")
public class DockerConfig {
    
    private MySQL mysql;
    private Network network;
    
    public static class MySQL {
        private String containerName;
        private String image;
        private Volume volume;
        
        public static class Volume {
            private String name;
            private String path;
            
            // Getters and Setters
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            
            public String getPath() { return path; }
            public void setPath(String path) { this.path = path; }
        }
        
        // Getters and Setters
        public String getContainerName() { return containerName; }
        public void setContainerName(String containerName) { this.containerName = containerName; }
        
        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }
        
        public Volume getVolume() { return volume; }
        public void setVolume(Volume volume) { this.volume = volume; }
    }
    
    public static class Network {
        private String name;
        private String driver;
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDriver() { return driver; }
        public void setDriver(String driver) { this.driver = driver; }
    }
    
    // Getters and Setters
    public MySQL getMysql() { return mysql; }
    public void setMysql(MySQL mysql) { this.mysql = mysql; }
    
    public Network getNetwork() { return network; }
    public void setNetwork(Network network) { this.network = network; }
    
    /**
     * Get the Docker Compose command for starting MySQL
     */
    public String getDockerComposeCommand() {
        return "docker-compose up -d mysql";
    }
    
    /**
     * Get the Docker Compose command for stopping MySQL
     */
    public String getDockerComposeStopCommand() {
        return "docker-compose down";
    }
    
    /**
     * Get the Docker Compose command for viewing logs
     */
    public String getDockerComposeLogsCommand() {
        return "docker-compose logs mysql";
    }
    
    @Override
    public String toString() {
        return "DockerConfig{" +
                "mysql=" + mysql +
                ", network=" + network +
                '}';
    }
} 
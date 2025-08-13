package com.dbVybe.app.cluster;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Scheduler;
import akka.cluster.typed.Cluster;
import akka.management.javadsl.AkkaManagement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract class for cluster nodes providing common functionality
 */
public abstract class ClusterNode {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterNode.class);
    
    protected final String nodeName;
    protected final String configPath;
    protected ActorSystem<Void> actorSystem;
    
    protected ClusterNode(String nodeName, String configPath) {
        this.nodeName = nodeName;
        this.configPath = configPath;
    }
    
    /**
     * Start the cluster node
     */
    public void start() {
        logger.info("Starting {} cluster node", nodeName);
        
        // Load configuration
        Config config = ConfigFactory.load(configPath);
        
        // Create actor system
        Behavior<Void> rootBehavior = createRootBehavior();
        actorSystem = ActorSystem.create(rootBehavior, nodeName, config);
        
        // Start Akka Management
        AkkaManagement.get(actorSystem).start();
        
        // Get cluster and log membership
        Cluster cluster = Cluster.get(actorSystem);
        cluster.manager().tell(akka.cluster.typed.Join.create(cluster.selfMember().address()));
        
        logger.info("{} cluster node started successfully", nodeName);
    }
    
    /**
     * Stop the cluster node
     */
    public void stop() {
        if (actorSystem != null) {
            logger.info("Stopping {} cluster node", nodeName);
            actorSystem.terminate();
            logger.info("{} cluster node stopped", nodeName);
        }
    }
    
    /**
     * Create the root behavior for this node
     * Each node should implement this to define its specific actor hierarchy
     */
    protected abstract Behavior<Void> createRootBehavior();
    
    /**
     * Get the actor system
     */
    public ActorSystem<Void> getActorSystem() {
        return actorSystem;
    }
    
    /**
     * Get the node name
     */
    public String getNodeName() {
        return nodeName;
    }
    
    /**
     * Get the scheduler for this node
     */
    public Scheduler getScheduler() {
        if (actorSystem != null) {
            return actorSystem.scheduler();
        }
        throw new IllegalStateException("ActorSystem not started");
    }
} 
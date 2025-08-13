package com.dbVybe.app.cluster;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Receive;
import com.dbVybe.app.actor.analysis.SchemaAnalysisActor;
import com.dbVybe.app.actor.analysis.VectorizationActor;
import com.dbVybe.app.actor.analysis.GraphActor;
import com.dbVybe.app.service.agent.DatabaseSchemaAgent;
import com.dbVybe.app.service.agent.VectorAnalysisAgent;
import com.dbVybe.app.service.agent.GraphAnalysisAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Node 3: Data Analysis Node with DataAnalysisSystem as the ActorSystem
 * 
 * This node handles:
 * - Data analysis and insights
 * - Statistical processing
 * - Data visualization preparation
 * - Pattern recognition
 * - Database schema analysis and vector embeddings
 */
public class DataAnalysisSystem extends ClusterNode {
    
    private static final Logger logger = LoggerFactory.getLogger(DataAnalysisSystem.class);
    private ActorRef<SchemaAnalysisActor.Command> schemaAnalysisActor;
    private ActorRef<VectorizationActor.Command> vectorizationActor;
    private ActorRef<GraphActor.Command> graphActor;
    
    // AI Agents
    private DatabaseSchemaAgent databaseSchemaAgent;
    private VectorAnalysisAgent vectorAnalysisAgent;
    private GraphAnalysisAgent graphAnalysisAgent;
    
    public DataAnalysisSystem() {
        super("DataAnalysisSystem", "application-data-analysis-node.conf");
    }
    
    public DataAnalysisSystem(DatabaseSchemaAgent databaseSchemaAgent, 
                             VectorAnalysisAgent vectorAnalysisAgent, 
                             GraphAnalysisAgent graphAnalysisAgent) {
        super("DataAnalysisSystem", "application-data-analysis-node.conf");
        this.databaseSchemaAgent = databaseSchemaAgent;
        this.vectorAnalysisAgent = vectorAnalysisAgent;
        this.graphAnalysisAgent = graphAnalysisAgent;
    }
    
    @Override
    protected Behavior<Void> createRootBehavior() {
        return Behaviors.setup(context -> {
            logger.info("Creating DataAnalysisSystem root behavior with Schema Analysis integration");
            
            // Create the main supervisor actor for data analysis
            ActorRef<DataAnalysisSupervisor.Command> supervisor = 
                context.spawn(DataAnalysisSupervisor.create(), "data-analysis-supervisor");
            
            // Create analysis actors with their respective agents
            if (databaseSchemaAgent != null) {
                schemaAnalysisActor = context.spawn(
                    SchemaAnalysisActor.create(databaseSchemaAgent), 
                    "schema-analysis-actor"
                );
                logger.info("Created SchemaAnalysisActor with Database Schema Agent integration");
            } else {
                logger.warn("DatabaseSchemaAgent not provided. Schema analysis will not be available.");
            }
            
            if (vectorAnalysisAgent != null) {
                vectorizationActor = context.spawn(
                    VectorizationActor.create(vectorAnalysisAgent),
                    "vectorization-actor"
                );
                logger.info("Created VectorizationActor with Vector Analysis Agent integration");
            } else {
                logger.warn("VectorAnalysisAgent not provided. Vectorization analysis will not be available.");
            }
            
            if (graphAnalysisAgent != null) {
                graphActor = context.spawn(
                    GraphActor.create(graphAnalysisAgent),
                    "graph-actor"
                );
                logger.info("Created GraphActor with Graph Analysis Agent integration");
            } else {
                logger.warn("GraphAnalysisAgent not provided. Graph analysis will not be available.");
            }
            
            logger.info("DataAnalysisSystem actor hierarchy created with Schema: {}, Vector: {}, Graph: {} integration", 
                databaseSchemaAgent != null ? "✓" : "✗",
                vectorAnalysisAgent != null ? "✓" : "✗",
                graphAnalysisAgent != null ? "✓" : "✗");
            
            return Behaviors.empty();
        });
    }
    
    /**
     * Get SchemaAnalysisActor reference
     */
    public ActorRef<SchemaAnalysisActor.Command> getSchemaAnalysisActor() {
        return schemaAnalysisActor;
    }
    
    /**
     * Get VectorizationActor reference
     */
    public ActorRef<VectorizationActor.Command> getVectorizationActor() {
        return vectorizationActor;
    }
    
    /**
     * Get GraphActor reference
     */
    public ActorRef<GraphActor.Command> getGraphActor() {
        return graphActor;
    }
    
    /**
     * Main supervisor actor for data analysis services
     */
    public static class DataAnalysisSupervisor extends AbstractBehavior<DataAnalysisSupervisor.Command> {
        
        public interface Command {}
        
        public static class AnalyzeData implements Command {
            private final String dataSet;
            private final String analysisType;
            private final String requestId;
            
            public AnalyzeData(String dataSet, String analysisType, String requestId) {
                this.dataSet = dataSet;
                this.analysisType = analysisType;
                this.requestId = requestId;
            }
            
            public String getDataSet() {
                return dataSet;
            }
            
            public String getAnalysisType() {
                return analysisType;
            }
            
            public String getRequestId() {
                return requestId;
            }
        }
        
        public static class StopAnalysis implements Command {}
        
        public static Behavior<Command> create() {
            return Behaviors.setup(DataAnalysisSupervisor::new);
        }
        
        private final Logger logger = LoggerFactory.getLogger(DataAnalysisSupervisor.class);
        
        private DataAnalysisSupervisor(ActorContext<Command> context) {
            super(context);
            logger.info("DataAnalysisSupervisor created");
        }
        
        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                .onMessage(AnalyzeData.class, this::onAnalyzeData)
                .onMessage(StopAnalysis.class, this::onStopAnalysis)
                .build();
        }
        
        private Behavior<Command> onAnalyzeData(AnalyzeData command) {
            logger.info("Analyzing data: {} with type: {} for request ID: {}", 
                command.getDataSet(), command.getAnalysisType(), command.getRequestId());
            // TODO: Implement data analysis logic
            return Behaviors.same();
        }
        
        private Behavior<Command> onStopAnalysis(StopAnalysis command) {
            logger.info("Stopping data analysis");
            // TODO: Implement cleanup logic
            return Behaviors.same();
        }
    }
} 
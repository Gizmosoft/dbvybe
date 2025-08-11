package com.dbVybe.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service locator to provide Spring-managed services to Akka actors
 */
@Component
public class ActorServiceLocator {
    
    private static UserDatabaseService userDatabaseService;
    private static UserSessionDatabaseService userSessionDatabaseService;
    private static UserDatabaseConnectionService userDatabaseConnectionService;
    
    @Autowired
    public void setUserDatabaseService(UserDatabaseService userDatabaseService) {
        ActorServiceLocator.userDatabaseService = userDatabaseService;
    }
    
    @Autowired
    public void setUserSessionDatabaseService(UserSessionDatabaseService userSessionDatabaseService) {
        ActorServiceLocator.userSessionDatabaseService = userSessionDatabaseService;
    }
    
    @Autowired
    public void setUserDatabaseConnectionService(UserDatabaseConnectionService userDatabaseConnectionService) {
        ActorServiceLocator.userDatabaseConnectionService = userDatabaseConnectionService;
    }
    
    public static UserDatabaseService getUserDatabaseService() {
        return userDatabaseService;
    }
    
    public static UserSessionDatabaseService getUserSessionDatabaseService() {
        return userSessionDatabaseService;
    }
    
    public static UserDatabaseConnectionService getUserDatabaseConnectionService() {
        return userDatabaseConnectionService;
    }
} 
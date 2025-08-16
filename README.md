# dbVybe  

dbVybe is a **no-code, multi-agent database exploration tool** which allows you to explore any database using natural language.  

## Local Development Setup  

- Clone the repository from `https://github.com/Gizmosoft/dbvybe.git`  
- Start the backend server by:  
`cd /backend/src/main/java/com/dbVybe/app`  
Run the `DbVybeApplication.java` class to start the spring boot server which will also fire the Akka cluster nodes on different ports  
- Start the frontend React+Vite app by:  
`cd /frontend`  
Run the command `npm run dev` to start the Vite server
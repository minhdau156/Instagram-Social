#Stage 1 : Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

#copy file pom the dependencies first 
#so if the source code change it don't need to resolve again
COPY pom.xml .

#Download dependencies
RUN mvn dependency:go-offline -q

#Copy source code
COPY src ./src

#Package the application, skipping tests
RUN mvn package -DskipTests -q

#Stage 2 : Run
FROM eclipse-temurin:21-jre-alpine AS run

#Create a group and a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

#Copy only the jar file from the build stage
COPY --from=build /app/target/social-media-backend-0.0.1-SNAPSHOT.jar app.jar

# Hand ownership of the workdir to the non-root user.
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

#Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
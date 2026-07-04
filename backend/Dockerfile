# Stage 1: Build the application using Maven
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml and download dependencies
# This caches dependencies as long as pom.xml doesn't change
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of the source code
COPY src ./src

# Build the application, skipping tests to ensure a fast build in deployment
RUN mvn clean package -DskipTests

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/enterprise-sla-engine-1.0.0.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

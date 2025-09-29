# syntax=docker/dockerfile:1.7

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/app

# Prepare Maven wrapper and download dependencies
COPY pom.xml mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

# Copy sources and build the application
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

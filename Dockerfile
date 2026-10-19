# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml e maven wrapper
COPY pom.xml .


# Download dependencies
RUN mvn dependency:resolve

# Copy source code
COPY src src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ENV AUTH0_ISSUER=https://dev-s41b58hqkvvmdu13.us.auth0.com/ \
    AUTH0_AUDIENCE=https://api.acessor-investimentos \
    FRONTEND_URL=http://localhost:5173 \
    SERVER_PORT=8080

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

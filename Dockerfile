# Multi-stage Dockerfile for DocChat API
# Stage 1: Build the application with Maven & Temurin JDK 17
FROM maven:3.9.8-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime with Temurin JRE 17
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Run as non-root user for security best practices
RUN groupadd -r docchat && useradd -r -g docchat docchat

COPY --from=builder /build/target/docchat-api-1.0.0.jar app.jar
RUN chown -R docchat:docchat /app

USER docchat

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

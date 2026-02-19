# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

# Application version for cache busting - update this to force rebuild
ARG APP_VERSION=2025.12.1
RUN echo "Building version: $APP_VERSION"

WORKDIR /build

# Copy parent pom and modify to only include needed modules
COPY pom.xml ./pom.xml

# Create a modified parent pom that only includes presence-service and teamsync-common
RUN sed -i 's/<module>api-gateway<\/module>//g' pom.xml && \
    sed -i 's/<module>content-service<\/module>//g' pom.xml && \
    sed -i 's/<module>storage-service<\/module>//g' pom.xml && \
    sed -i 's/<module>sharing-service<\/module>//g' pom.xml && \
    sed -i 's/<module>team-service<\/module>//g' pom.xml && \
    sed -i 's/<module>project-service<\/module>//g' pom.xml && \
    sed -i 's/<module>workflow-execution-service<\/module>//g' pom.xml && \
    sed -i 's/<module>trash-service<\/module>//g' pom.xml && \
    sed -i 's/<module>search-service<\/module>//g' pom.xml && \
    sed -i 's/<module>chat-service<\/module>//g' pom.xml && \
    sed -i 's/<module>notification-service<\/module>//g' pom.xml && \
    sed -i 's/<module>activity-service<\/module>//g' pom.xml && \
    sed -i 's/<module>wopi-service<\/module>//g' pom.xml && \
    sed -i 's/<module>settings-service<\/module>//g' pom.xml && \
    sed -i 's/<module>permission-manager-service<\/module>//g' pom.xml && \
    sed -i 's/<module>signing-service<\/module>//g' pom.xml && \
    sed -i 's/<module>audit-service<\/module>//g' pom.xml

# Copy module poms (teamsync-common depends on teamsync-common-core)
COPY teamsync-common-core/pom.xml ./teamsync-common-core/pom.xml
COPY teamsync-common/pom.xml ./teamsync-common/pom.xml
COPY presence-service/pom.xml ./presence-service/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl presence-service -am -B || true

# Copy source code
COPY teamsync-common-core/src ./teamsync-common-core/src
COPY teamsync-common/src ./teamsync-common/src
COPY presence-service/src ./presence-service/src

# Build the application
# Use maven.test.skip=true to skip both test compilation and execution
RUN mvn clean package -pl presence-service -am -Dmaven.test.skip=true -B

# Stage 2: Runtime image (Debian Slim)
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user for security
RUN groupadd -r teamsync && useradd -r -g teamsync teamsync

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy the jar file from builder
COPY --from=builder /build/presence-service/target/presence-service-*.jar app.jar

# Set ownership
RUN chown -R teamsync:teamsync /app

# Switch to non-root user
USER teamsync

# Expose the port
EXPOSE 9095

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -Xss256k \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Health check with extended start period for JVM warm-up
HEALTHCHECK --interval=30s --timeout=15s --start-period=180s --retries=5 \
  CMD curl -f http://localhost:9095/actuator/health || exit 1

# Run the application with JVM options and profile activation
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-local} -jar app.jar"]

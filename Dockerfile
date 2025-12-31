FROM maven:3.9-eclipse-temurin-21 AS builder

# Copy source code
COPY . /app
WORKDIR /app

# Build the application using Maven
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

ENV LANGUAGE='en_US:en'

# Create app directory and user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN mkdir -p /deployments && chown -R appuser:appuser /deployments

# Copy the built application from builder stage
COPY --from=builder --chown=appuser:appuser /app/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=appuser:appuser /app/target/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=appuser:appuser /app/target/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=appuser:appuser /app/target/quarkus-app/quarkus/ /deployments/quarkus/

WORKDIR /deployments
EXPOSE 8080
USER appuser

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
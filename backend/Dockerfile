# ─── Stage 1: Build ─────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# ─── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="pandac-chat-backend" \
      org.opencontainers.image.description="AI-powered personal chat widget backend — Spring Boot + Apache Camel + Spring AI" \
      org.opencontainers.image.source="https://github.com/pandaind/pandac-chat"

WORKDIR /app

# Create data directory for H2 persistence
RUN mkdir -p /app/data

# Copy built JAR
COPY --from=builder /app/target/pandac-chat-backend-*.jar app.jar

# Bake in the default personal context (can be overridden with a volume mount)
COPY data/personal-context.txt /app/data/personal-context.txt

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

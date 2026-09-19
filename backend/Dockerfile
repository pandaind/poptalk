# ─── Stage 1: Build ─────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# ─── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="poptalk-backend" \
      org.opencontainers.image.description="PopTalk — self-hostable AI chat widget backend for any website. Spring Boot + Apache Camel + Spring AI" \
      org.opencontainers.image.source="https://github.com/pandaind/poptalk"

WORKDIR /app

# Create data directory for H2 persistence
RUN mkdir -p /app/data

# Copy built JAR (finalName is fixed in pom.xml, so no version suffix to track here)
COPY --from=builder /app/target/poptalk-backend.jar app.jar

# Bake in the default context (can be overridden with a volume mount)
COPY data/context.txt /app/data/context.txt

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

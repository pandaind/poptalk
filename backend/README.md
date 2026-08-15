# PandaC Chat Backend

A Spring Boot + Apache Camel backend for the `pandac.in` popup chat widget.

## Features
- Integrates directly with Telegram Bot API via `camel-telegram` (Producer and Consumer).
- H2 file-based persistence for chat sessions and message history (survives restarts).
- Stateless REST API secured with HS256 JWTs.
- IP-based rate limiting on registration endpoints.
- Auto-purges sessions older than 7 days.

## Development

1. Setup environment variables:
   ```bash
   cp .env.example .env
   # Edit .env with your real credentials
   ```

2. Run with dev profile:
   ```bash
   export $(cat .env | xargs)
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. The dev profile:
   - Sets CORS allowed origin to `http://localhost:1313`
   - Enables H2 console at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/pandac-chat`)
   - Enables DEBUG logging for Camel routes.

## Deployment
Build the fat JAR:
```bash
./mvnw clean package
```
Run the JAR:
```bash
java -jar target/pandac-chat-backend-1.0.0.jar
```
*Ensure all environment variables from `.env.example` are set on the server.*


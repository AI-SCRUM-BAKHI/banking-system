# Scrum-AI Backend

Foundation backend for the Scrum-AI project.

## Stack

- Java 21
- Spring Boot 4.0.8
- Gradle 9.x
- PostgreSQL 16
- Flyway
- Spring Data JPA
- Springdoc OpenAPI

## Project structure

```text
app-bootstrap/  Spring Boot entry point and web configuration
common/         API responses, exceptions, validation and request ID
database/       JPA configuration and Flyway migrations
```

## Run locally

Prerequisites: JDK 21, Docker and Docker Compose.

```powershell
Copy-Item .env.example .env
docker compose up -d
.\gradlew.bat :app-bootstrap:bootRun
```

Or use:

```powershell
.\scripts\run-local.ps1
```

## Verify

- Health: http://localhost:8080/actuator/health
- Ping: http://localhost:8080/api/v1/system/ping
- Swagger: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs

## Quality checks

```powershell
.\gradlew.bat formatCode
.\gradlew.bat qualityGate
```

## Database migrations

Add a new migration under:

```text
database/src/main/resources/db/migration/
```

Use the naming format `V{number}__{description}.sql`. Never edit an already-applied migration.

## API response format

Successful responses use `ApiResponse<T>`. Errors use `ApiErrorResponse` with an error code, HTTP status, request ID and optional details.

# Banking Fraud Detection

Monorepo for the banking fraud detection product: a Spring Boot backend and a React operations console.

## Repository structure

```text
backend/       Java 21 + Spring Boot multi-module backend
frontend/      React + TypeScript + Vite operations console
infra/         Local infrastructure, currently PostgreSQL via Docker Compose
.github/       Backend and frontend CI workflows
docs/          Product and technical documentation (add as needed)
```

The backend keeps persistence entities in their owning Gradle modules:

- `backend/identity`: users, roles and user repository.
- `backend/fraud`: fraud transactions, rule hits, money-flow chain and repositories.
- `backend/database`: shared JPA setup, datasource URL handling and Flyway migrations.

The backend reads `backend/.env` when launched from that directory. Copy `.env.example` for local PostgreSQL. Database connection settings use one variable: `DATABASE_URL`, a PostgreSQL connection URI containing the username and password.

## Run locally

### Backend

Prerequisites: JDK 21 and Docker Desktop.

```powershell
Copy-Item backend/.env.example backend/.env
docker compose -f infra/docker-compose.yml up -d postgres
Push-Location backend
.\gradlew.bat :app-bootstrap:bootRun
Pop-Location
```

Backend endpoints:

- Health: http://localhost:8080/actuator/health
- Ping: http://localhost:8080/api/v1/system/ping
- Swagger: http://localhost:8080/swagger-ui.html

### Frontend

Prerequisites: Node.js 22.12+ (or Node.js 20.19+).

```powershell
Copy-Item frontend/.env.example frontend/.env
Push-Location frontend
npm install
npm run dev
Pop-Location
```

The Vite development server runs at http://localhost:5173.

## Quality checks

```powershell
Push-Location backend; .\gradlew.bat qualityGate; Pop-Location
Push-Location frontend; npm run format:check; npm run lint; npm run typecheck; npm run test:run; npm run build; Pop-Location
```

CI runs these checks independently when files in the corresponding project change.

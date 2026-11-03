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

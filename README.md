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

## Backend authentication and API

Copy `backend/.env.example` to `backend/.env`, then configure these values before starting the backend:

- `DATABASE_URL`: PostgreSQL JDBC URI used by Spring Boot and Flyway.
- `APP_JWT_SECRET`: base64 encoded random key with at least 32 bytes. Generate with `openssl rand -base64 32`.
- `APP_SERVICE_TOKEN`: random secret sent as `X-Service-Token` by the trusted fraud detection producer. Generate one with `openssl rand -hex 32`.
- `APP_BOOTSTRAP_ADMIN_*`: configure all four values to create the first administrator when the `users` table is empty. Use a password with at least 12 characters and at most 72 UTF-8 bytes. These values are ignored after a user exists.

The API is stateless. Login returns a 15 minute access token and a seven day refresh token. Refresh tokens are stored only as SHA-256 hashes and rotate when refreshed. Set a new `APP_JWT_SECRET` only when intentionally invalidating all existing access tokens.

### API groups

| Group | Routes | Access |
| --- | --- | --- |
| Authentication | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | Login and refresh are public; logout and me require a bearer token |
| User management | `/api/v1/users/**` | `ADMIN`, except `PATCH /api/v1/users/me/password` is available to the signed-in user |
| Fraud investigation | `/api/v1/fraud-transactions/**` | `ADMIN` and `BANK_USER` |
| Fraud ingest | `POST /api/v1/fraud-transactions` | Trusted producer with `X-Service-Token: <APP_SERVICE_TOKEN>` |

See [docs/api.md](docs/api.md) for request and response details. Flyway migration V3 adds the refresh-token table. The migration runs automatically on application startup after the database URL and secrets are configured.

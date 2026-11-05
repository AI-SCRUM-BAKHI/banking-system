# Banking Fraud Detection

Monorepo for the banking fraud detection product: a Spring Boot backend and a React operations console.

## Repository structure

```text
backend/       Java 21 + Spring Boot multi-module backend
frontend/      React + TypeScript + Vite operations console
infra/         Docker Compose for the frontend and backend
.github/       Backend and frontend CI workflows
docs/          Product and technical documentation (add as needed)
```

The backend keeps persistence entities in their owning Gradle modules:

- `backend/identity`: users, roles and user repository.
- `backend/fraud`: fraud transactions, rule hits, money-flow chain and repositories.
- `backend/database`: shared JPA setup, datasource URL handling and Flyway migrations.

The backend reads database and authentication settings from `backend/.env`. Set `DATABASE_URL` to the PostgreSQL connection URI for the existing database; Compose does not start a database container.

## Run frontend and backend with Docker Compose

Configure `backend/.env` with the database URL and required auth secrets, then run from the repository root:

```powershell
docker compose -f infra/docker-compose.yml up --build
```

Open the frontend at http://localhost:5173. The backend API is at http://localhost:8080 and Swagger is at http://localhost:8080/swagger-ui.html. Stop both containers with `Ctrl+C`, or run `docker compose -f infra/docker-compose.yml down` in another terminal.

The frontend defaults to `/api/v1` on its own origin. Its Nginx container forwards API and Swagger requests to `backend:8080`, so the same image works on localhost and the public server. The API URL is compiled into the bundle during image build. To call a separate API host directly, set `VITE_API_BASE_URL` before building and allow the frontend origin in `APP_CORS_ALLOWED_ORIGINS`.

## Run locally

### Backend

Prerequisites: JDK 21.

Configure `backend/.env` with your existing PostgreSQL URI and authentication
secrets before starting the backend. The local scripts do not start PostgreSQL.

Linux/macOS:

```bash
bash backend/scripts/run-local.sh
```

Windows PowerShell:

```powershell
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
- `APP_SERVICE_TOKEN`: random secret sent as `X-Service-Token` by the trusted transaction or fraud-result producer. Generate one with `openssl rand -hex 32`.
- `APP_BOOTSTRAP_ADMIN_*`: configure all four values to create the first administrator when the `users` table is empty. Use a password with at least 12 characters and at most 72 UTF-8 bytes. These values are ignored after a user exists.

The API is stateless. Login returns a 15 minute access token and a seven day refresh token. Refresh tokens are stored only as SHA-256 hashes and rotate when refreshed. Set a new `APP_JWT_SECRET` only when intentionally invalidating all existing access tokens.

### API groups

| Group | Routes | Access |
| --- | --- | --- |
| Authentication | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` | Login and refresh are public; logout and me require a bearer token |
| User management | `/api/v1/users/**` | `ADMIN`, except `PATCH /api/v1/users/me/password` is available to the signed-in user |
| Fraud investigation | `/api/v1/fraud-transactions/**` | `ADMIN` and `BANK_USER` |
| Fraud ingest | `POST /api/v1/fraud-transactions` | Trusted producer with `X-Service-Token: <APP_SERVICE_TOKEN>` |
| Source transaction ingest | `POST /api/v1/transactions` | Trusted producer; saves `PENDING` for background evaluation using `X-Service-Token` |
| Rule-based fraud detection | `POST /api/v1/transactions/evaluate` | Trusted producer with `X-Service-Token: <APP_SERVICE_TOKEN>` |
| AI fraud analysis | `GET` / `POST /api/v1/transactions/{transactionRef}/ai-analysis` | JWT bearer token; `ADMIN` and `BANK_USER` |

See [docs/api.md](docs/api.md) for request and response details. Flyway migration V3 adds the refresh-token table. The migration runs automatically on application startup after the database URL and secrets are configured.

## AI fraud analysis demo

The console can explain why an evaluated transaction was flagged and suggest
investigation steps using OpenRouter. Add `OPENROUTER_API_KEY` and
`OPENROUTER_MODEL` to the existing `backend/.env`; select a model/provider that
supports strict JSON schema outputs. The other settings and defaults are in
[backend/.env.example](backend/.env.example). Keep all provider secrets on the
backend. Flyway V5 creates the analysis cache on startup.

In live mode, open Transactions and use **Phân tích theo mã giao dịch** with a
reference whose evaluation returned `decision: FRAUD`. Matching investigation
cases also have a **Phân tích AI** tab. Demo mode displays a labeled synthetic
example without calling OpenRouter. See the [setup and demo guide](docs/ai-fraud-analysis.md)
for the full flow, prerequisites and troubleshooting.
### Background transaction scanner

The optional scanner evaluates source rows in `transactions` and creates fraud cases
without calling the POST APIs. It is disabled by default. See
[background transaction evaluation](docs/transaction-scan.md) for V5 rollout,
coverage requirements, settings and retry/reset procedures.

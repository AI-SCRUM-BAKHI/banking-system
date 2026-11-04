# AEGIS frontend

React + TypeScript console, built against the existing Spring controllers. Only `frontend/` was changed. Based on main commit `23bfed3`.

## Start

```powershell
cd D:\Scrum\banking-system\frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5174
```

Open http://127.0.0.1:5174 . Choose **Khám phá demo** to enter the sandbox without a backend.

For live mode, the default API base is `/api/v1` on the frontend origin. Docker Nginx forwards API and Swagger routes to `backend:8080`. Local Vite forwards API routes to `VITE_API_PROXY_TARGET` (default `http://localhost:8080/api/v1`). Start the backend independently for local development. You can override `VITE_API_BASE_URL` with an absolute URL for a separately hosted backend, which must allow the frontend origin through CORS. Rebuild the frontend image after changing build-time API settings. Docker Compose serves the frontend on port 5173. Keep server secrets outside VITE variables.

## Implemented

- Vietnamese landing page, animated radar/network visual, scroll reveals, pause control, reduced-motion support.
- Live username/password login, registration, bearer authorization and refresh-token rotation. The live account is retained when entering demo. The homepage shows the signed-in account and a workspace action, and demo provides a return-to-live action. Session data is held in sessionStorage for this tab.
- Dashboard using `/fraud-transactions/summary`; charts disclose their scope of at most 100 queried cases, not the full transaction universe.
- Server-paginated case search, risk/review/date filters, sorting, current-page CSV export.
- Case drawer with account details, rule hits, graph and review transitions matching the backend: NEW -> REVIEWING -> CONFIRMED_FRAUD or FALSE_POSITIVE.
- Interactive directed money-flow graph, node selection and related transfers.
- API query playground for the existing list, summary, detail, rules and graph endpoints. Producer docs include the actual ingest payload and server-side cURL example.
- Detect fraud form/JSON editor calls `POST /api/v1/transactions/evaluate` and renders nullable isFraud, riskScore, evaluationStatus, triggeredRules and unevaluatedRules. Historical CSV examples retry stored inputs idempotently; a fresh sample creates a new reference and timestamp.
- Admin-only member listing, creation and enable/disable actions. Public registration uses the existing backend registration endpoint.

## Explicit demo boundaries

Demo mode uses 168 deterministic synthetic cases (29 Oct–4 Nov 2026 Vietnam time) and no backend requests. Review changes are in memory and reset on page reload. Demo-created members also exist only until their page is remounted. API playground responses in demo are browser-generated envelopes labeled as simulated. Live API failures never switch to synthetic data automatically.

IP Intelligence is demo-only: the backend DTO has no IP field. Its clusters use documentation IP ranges. In live mode the page explains the unsupported capability and does not fabricate IP data. Confirmed review labels and high-risk classifications are counted separately.

The evaluation endpoint requires a service token on the backend. The frontend Nginx gateway first authenticates the caller through `GET /auth/me`, then calls the evaluator with the server-side `APP_SERVICE_TOKEN`. Docker Compose supplies runtime configuration from `backend/.env`; the token is excluded from Vite build variables and browser requests. Nginx envsubst is restricted to that token variable. Local Vite provides the same authenticated gateway and reads the service token only in its server config. Direct producer calls to port 8080 retain the backend service-token contract.

The evaluator stores results and transfer history separately from investigation cases. PARTIAL results below the fraud threshold return isFraud=null. Coverage must reflect the actual known source interval. Demo mode requires real sign-in to run evaluation, while its query playground continues to simulate read responses.

No security certifications, detection accuracy or real banking throughput are claimed. Demo records are not real customer transactions.

## Checks

```powershell
npm run format:check
npm run lint
npm run typecheck
npm run test:coverage
npm run build
```

Unit/contract tests cover backend envelope unwrapping, query filters, review transitions, IP grouping, snapshot date boundaries, token refresh concurrency and reduced-motion metric updates. Browser QA covers demo navigation, review, query playground, filtering, money-flow and responsive layouts. Backend localhost:8080 was unavailable during implementation; live server end-to-end login was not verified.

Design reference: Stripe Radar https://stripe.com/radar (product presentation and signal-to-investigation flow; original AEGIS artwork and UI).

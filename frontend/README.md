# AEGIS frontend

React + TypeScript console, built against the existing Spring controllers. Only `frontend/` was changed. Based on main commit `23bfed3`.

## Start

```powershell
cd D:\Scrum\banking-system\frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5174
```

Open http://127.0.0.1:5174 . Choose **Khám phá demo** to enter the sandbox without a backend.

For live mode, set `VITE_API_BASE_URL` in `frontend/.env` (default `http://localhost:8080/api/v1`), start the existing backend independently, then log in using a backend-provisioned account. Do not put service tokens or server secrets in any VITE variable. Existing Docker Compose uses the standard Vite port 5173 and remains unchanged.

## Implemented

- Vietnamese landing page, animated radar/network visual, scroll reveals, pause control, reduced-motion support.
- Live username/password login, bearer authorization, shared refresh-token rotation on concurrent 401s, logout and session-expiration handling. Tokens/profile are held in sessionStorage for this browser tab.
- Dashboard using `/fraud-transactions/summary`; charts disclose their scope of at most 100 queried cases, not the full transaction universe.
- Server-paginated case search, risk/review/date filters, sorting, current-page CSV export.
- Case drawer with account details, rule hits, graph and review transitions matching the backend: NEW -> REVIEWING -> CONFIRMED_FRAUD or FALSE_POSITIVE.
- Interactive directed money-flow graph, node selection and related transfers.
- API query playground for the existing list, summary, detail, rules and graph endpoints. Producer docs include the actual ingest payload and server-side cURL example.
- Admin-only member listing, creation and enable/disable actions. Public registration explains admin-provisioned access, because backend has no public signup.

## Explicit demo boundaries

Demo mode uses 168 deterministic synthetic cases (29 Oct–4 Nov 2026 Vietnam time) and no backend requests. Review changes are in memory and reset on page reload. Demo-created members also exist only until their page is remounted. API playground responses in demo are browser-generated envelopes labeled as simulated. Live API failures never switch to synthetic data automatically.

IP Intelligence is demo-only: the backend DTO has no IP field. Its clusters use documentation IP ranges. In live mode the page explains the unsupported capability and does not fabricate IP data. Confirmed review labels and high-risk classifications are counted separately.

`POST /transactions/evaluate` is a proposal in `docs/Spec.md`, not an existing endpoint. This frontend does not implement a scoring backend. Existing `POST /fraud-transactions` ingests precomputed scoring results; it requires `X-Service-Token` from a trusted producer. The frontend never stores or sends that token.

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

# Backend API

All successful responses use the common envelope `{ success, data, message, timestamp, requestId }`. Errors use `{ success, error: { code, message, details }, statusCode, timestamp, path, requestId }`.

## Test in Swagger UI

Open `http://localhost:8080/swagger-ui/index.html` while the backend is running.

1. Expand `POST /api/v1/auth/login`, click **Try it out**, enter your username and
   password, and click **Execute**. Copy `data.accessToken` from the response.
2. Click **Authorize** at the top. Under `bearerAuth`, paste the access token alone
   (Swagger adds `Bearer` automatically), click **Authorize**, then **Close**.
3. Expand a fraud GET endpoint, click **Try it out**, fill its parameters, and
   click **Execute**. View the JSON under **Response body**.
4. For `POST /api/v1/fraud-transactions` or `POST /api/v1/transactions/evaluate`,
   authorize `serviceToken` using `APP_SERVICE_TOKEN` from `backend/.env`.
   Swagger sends it in `X-Service-Token`. Fill the request body and click **Execute**.

If a bearer token expires, log in again and replace it in **Authorize**. Use a new
transaction reference (also in the root chain entry) when creating another fraud
fixture; the ingest endpoint returns HTTP 409 for an existing reference.

## Authentication

`POST /api/v1/auth/login`

```json
{ "username": "operator", "password": "your-password" }
```

Returns `accessToken`, `refreshToken`, `tokenType`, and `expiresIn` seconds. Use the access token in `Authorization: Bearer <token>`.

`POST /api/v1/auth/refresh` takes `{ "refreshToken": "..." }`; it revokes that refresh token and returns a rotated pair. `POST /api/v1/auth/logout` takes the same body and revokes the caller's refresh token. `GET /api/v1/auth/me` returns the current user without password fields.

## Users

`POST /api/v1/users` creates a bank user/admin. `GET /api/v1/users?page=0&size=20&role=BANK_USER&status=ACTIVE` lists users. `GET /api/v1/users/{id}` reads a user. `PATCH /api/v1/users/{id}` updates profile fields and role; `PATCH /api/v1/users/{id}/status` changes status; `PATCH /api/v1/users/{id}/password` resets a password. These management operations require `ADMIN`.

`PATCH /api/v1/users/me/password` changes the authenticated user's password using `currentPassword` and `newPassword`.

## Fraud

Fraud create accepts one aggregate containing transaction fields, `ruleHits`, and a `transactionChain` with exactly one matching root. `POST /api/v1/fraud-transactions` is reserved for the trusted producer service token. Bank users and admins can query `GET /api/v1/fraud-transactions` with pagination and filters, `GET /api/v1/fraud-transactions/{id}`, `GET /api/v1/fraud-transactions/{id}/rule-hits`, and `GET /api/v1/fraud-transactions/{id}/graph`. `PATCH /api/v1/fraud-transactions/{id}/review-status` updates the review state, and `GET /api/v1/fraud-transactions/summary` returns dashboard counts.

## Transaction risk evaluation

`POST /api/v1/transactions/evaluate` runs all seven rules in
[risk_score_spec.md](risk_score_spec.md), version `rule-score-v1`. Supply
`X-Service-Token: <APP_SERVICE_TOKEN>`. The existing fraud ingest endpoint stores
externally calculated results; it does not run this engine. Evaluation results
are stored separately and are not automatically added to the investigation dashboard.

```json
{
  "transactionRef": "TX-001",
  "senderAccountId": "ACCOUNT-A",
  "receiverAccountId": "ACCOUNT-B",
  "amountVnd": 1000000,
  "currency": "VND",
  "transactionTime": "2026-10-01T08:00:00+07:00",
  "postedAt": "2026-10-01T08:00:01+07:00"
}
```

`postedAt` is optional. Times require an explicit UTC offset; the transaction
must have occurred and posting must be between transaction time and evaluation
time. Only positive whole VND amounts and distinct accounts are accepted. No
client score, scenario label, baseline, or coverage is used as a scoring feature.

The common response envelope contains `transactionRef`, nullable `isFraud`,
`decision`, `riskScore`, `riskLevel`, `evaluationStatus`, `fraudThreshold`,
`ruleVersion`, `triggeredRules`, and `unevaluatedRules`. Suppressed matches remain
in `triggeredRules` with zero contribution and `suppressedBy`. Missing required
context produces `PARTIAL`; below 60 points this means `isFraud: null` and
`INSUFFICIENT_DATA`, while a score of at least 60 still predicts `FRAUD`.

Ingest **all successful transfers**, including normal transactions, through this
endpoint in globally nondecreasing `transactionTime` order. Backfill history first,
before submitting newer transactions. A database row lock serializes evaluation
across application instances. Same-reference retries with equivalent normalized
transaction data return the original result/version before checking chronology;
changed data or a new late transaction returns HTTP 409. Failed evaluation rolls
back history, audit, result, and watermark together.

Migration `V4__create_risk_evaluation_context.sql` adds:

- `risk_transfer_history`: complete successful-transfer input ledger, immutable
  original result, and audit JSON containing config, context bounds, per-rule
  status/features/evidence, and stable `riskEventId` hashes.
- `risk_ingest_state`: global ordering and serialization lock.
- `risk_account_coverage`: trusted, account-specific contiguous coverage intervals
  for outbound transaction time and inbound **posting time**, plus account opening
  time and posting completeness.

Coverage starts unknown. The evaluator never infers complete source coverage
from the absence of rows. After verifying that all source records for an interval
have been ingested, the trusted importer/operator must certify coverage in
`risk_account_coverage`. There is deliberately no caller-facing coverage API.
For example, after checking the source ledger for `ACCOUNT-A`:

```sql
BEGIN;
SELECT id FROM risk_ingest_state WHERE id = 1 FOR UPDATE;
INSERT INTO risk_account_coverage
  (account_id, opened_at, outbound_from, outbound_through,
   inbound_posted_from, inbound_posted_through, posting_complete)
VALUES
  ('ACCOUNT-A', NULL,
   '2026-09-01T00:00:00Z', '2026-10-01T01:00:00Z',
   '2026-09-01T00:00:00Z', '2026-10-01T01:00:00Z', TRUE)
ON CONFLICT (account_id) DO UPDATE SET
  opened_at = EXCLUDED.opened_at,
  outbound_from = EXCLUDED.outbound_from,
  outbound_through = EXCLUDED.outbound_through,
  inbound_posted_from = EXCLUDED.inbound_posted_from,
  inbound_posted_through = EXCLUDED.inbound_posted_through,
  posting_complete = EXCLUDED.posting_complete;
COMMIT;
```

Only certify verified intervals; never extend them across a data gap or beyond
the trusted source's completeness watermark. Extend coverage as the source
advances, holding the same ingest-state lock. Set `opened_at` only when known;
it permits shorter complete history for new accounts but never bypasses the
20-sample baseline requirement. Missing posting information on an earlier inbound
conservatively prevents money-flow evaluation because it could represent delayed
posting. Posting updates to already evaluated transfers and late-event replay
require a separate importer/reconciliation extension; retries cannot change an
existing transfer. Prior online results remain unchanged after coverage advances.

## Background source evaluation

The optional background scanner evaluates rows in `transactions` and creates fraud
cases internally. Existing POST endpoints remain available with their current
service-token authentication and behavior. See [transaction-scan.md](transaction-scan.md)
for migration, source contracts, coverage, configuration and manual reevaluation.

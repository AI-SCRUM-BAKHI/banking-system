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

## AI fraud analysis

Both endpoints require `Authorization: Bearer <accessToken>` for `ADMIN` or
`BANK_USER`. A producer service token does not authorize AI analysis.
`transactionRef` identifies a saved evaluation in `risk_transfer_history`, not
the UUID of a fraud investigation case. References must be nonblank and at most
80 characters. Use URL-safe references (for example letters, digits, hyphens
and underscores). URL-encode references when building the route, but avoid
slashes: the default security firewall rejects encoded slashes. The evaluation
endpoint's existing reference contract is unchanged, so a legacy reference
containing a slash cannot be accessed through this new path-based route.

| Method and route | Behavior |
| --- | --- |
| `POST /api/v1/transactions/{transactionRef}/ai-analysis` | Return saved analysis, or synchronously call OpenRouter, validate and save it. No request body. Requires original `decision = FRAUD` and usable matched-rule evidence. |
| `GET /api/v1/transactions/{transactionRef}/ai-analysis` | Return the saved analysis. Does not call OpenRouter or generate a result. |

Both return HTTP 200 with the common success envelope. The `data` object has
two business fields, illustrated below; identifiers and amounts in a real
response must be grounded in that evaluation's evidence.

```json
{
  "success": true,
  "data": {
    "fraudExplanation": {
      "summary": "Giao dịch bị gắn cờ do nhận tiền rồi chuyển tiếp nhanh.",
      "reasons": [
        {
          "ruleCode": "RAPID_PASS_THROUGH",
          "description": "Tài khoản chuyển đi 97% số tiền vừa nhận sau 90 giây.",
          "evidenceTransactionRefs": ["TX-IN-001", "TX-OUT-002"]
        }
      ]
    },
    "investigationRecommendations": [
      {
        "priority": 1,
        "action": "Xác minh mục đích nhận tiền và chuyển tiếp.",
        "rationale": "Chuỗi giao dịch có dấu hiệu tài khoản trung gian.",
        "evidenceTransactionRefs": ["TX-IN-001", "TX-OUT-002"],
        "dataToCollect": ["Chứng từ giao dịch", "Quan hệ giữa các bên"]
      }
    ]
  },
  "message": "Request completed successfully",
  "timestamp": "2026-11-05T00:00:00Z",
  "requestId": "example-request-id"
}
```

Analysis uses the original request, result and audit JSON. Reasons must cite
matched rule codes and evidence refs belonging to that rule. Recommendations
must cite refs in the permitted evidence set. The server validates nonempty
fields, bounded list/text sizes and unique priorities from 1 to 5. These checks
do not verify every narrative claim; investigation recommendations need human
review. AI does not update scores, evaluation decisions or review statuses.
Every triggered rule with a positive score contribution must appear in the
explanation; an otherwise well-formed response that omits one is rejected.

Migration `V5__create_fraud_ai_analysis.sql` stores one immutable analysis per
reference in `fraud_ai_analysis`, together with a SHA-256 input snapshot hash,
model, prompt version and creation time. Repeated POST calls return that saved
analysis. Changing model or env configuration does not regenerate old results.
There is no regeneration endpoint. Same-reference requests serialize within
one JVM; across instances duplicate provider requests remain possible, while
the database uniqueness constraint preserves one winning saved result.

| HTTP | Error code | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Reference is blank or exceeds 80 characters. |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired bearer token. |
| 403 | `ACCESS_DENIED` | Authenticated caller lacks a permitted role. |
| 404 | `RESOURCE_NOT_FOUND` | No evaluation exists for the reference. |
| 404 | `AI_ANALYSIS_NOT_FOUND` | Evaluation exists; analysis has not been created. |
| 409 | `AI_ANALYSIS_NOT_ELIGIBLE` | Decision is not `FRAUD`, matched evidence is missing, or serialized input exceeds the current 150,000-character limit. |
| 502 | `AI_PROVIDER_ERROR` | OpenRouter/network failure, unsupported schema/model, truncated or malformed output, or output failed grounding validation. |
| 503 | `AI_PROVIDER_UNAVAILABLE` | Backend API key or model is not configured. |
| 504 | `AI_PROVIDER_TIMEOUT` | OpenRouter request exceeded the configured timeout. |

Provider response bodies and API keys are not returned to callers. Failed
generation is not saved; retry POST to try again. See
[AI fraud analysis setup](ai-fraud-analysis.md) for environment configuration,
frontend behavior and a live demo checklist.

# AI fraud analysis demo

The feature explains a saved rule-engine fraud decision and recommends
investigation steps in Vietnamese. OpenRouter receives the evaluated
transaction, saved result and audit; the console displays two sections with
rule and transaction evidence references.

## Prerequisites

- Existing Java 21/Spring Boot backend, PostgreSQL and React console setup from
  the [README](../README.md).
- An OpenRouter API key with access to a model/provider supporting strict JSON
  schema responses.
- A live `ADMIN` or `BANK_USER` login.
- At least one saved evaluation whose `decision` is `FRAUD` for a live demo.

## Configure the backend

Append these settings to your existing `backend/.env`. Preserve the existing
database URL, JWT secret, service token and bootstrap admin configuration.
For a new setup only, copy `backend/.env.example` to `backend/.env` before
filling all required values. Do not replace an already configured env file.

```dotenv
OPENROUTER_API_KEY=your-openrouter-api-key
OPENROUTER_MODEL=openai/gpt-4o-mini
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_TIMEOUT_SECONDS=60
```

The application defaults the key and model to empty when not configured; the
example uses `openai/gpt-4o-mini`, listed with `structured_outputs` support in
the [OpenRouter model catalog](https://openrouter.ai/api/v1/models).
You can substitute another model with the same capability.
`BASE_URL` defaults to the address above;
the backend appends `/chat/completions`. `TIMEOUT_SECONDS` defaults to 60 and is
bounded to 1–120 seconds by the client. The frontend allows 120 seconds for
generation; the default 60-second backend timeout leaves room to receive an
error response. Restart the backend after editing settings.

Choose an exact model ID from OpenRouter with structured-output support. The
request uses `response_format.type=json_schema`, `strict=true` and provider
`require_parameters=true`; a model/provider without support will fail rather
than silently return unstructured text. No particular model is hardcoded.

Keep OpenRouter variables in `backend/.env`; never put the API key in a
`VITE_*` variable or frontend code. Requests send evaluation and audit content
to the configured provider, so use synthetic data for this demo. The provider
key is sent only by the backend.

Docker Compose already reads `backend/.env` through `env_file`. Recreate the
backend container after changing its configuration. Flyway automatically runs
V10 on application startup to create `fraud_ai_analysis`; no manual table
creation is needed.

## Prepare a live transaction

`POST /api/v1/transactions/evaluate` must have returned `decision: FRAUD` for
the reference you will use. Ingest complete successful-transfer history in
chronological order first, and have the trusted importer/operator certify
the verified history coverage. See [transaction risk evaluation](api.md#transaction-risk-evaluation)
and [risk score specification](risk_score_spec.md).

Submitting a single high-value transaction does not guarantee a fraud
decision: the amount baseline and money-flow rules require history and
coverage. Unknown coverage can produce `INSUFFICIENT_DATA`. For a repeatable
demo, prepare a known synthetic fixture that activates enough rules and
check the actual evaluation response before requesting analysis. Do not
certify coverage beyond the verified source ledger.

Evaluations and investigation cases are separate stores:

- Evaluating a transaction does not automatically create a dashboard case.
- Creating a case through `/fraud-transactions` does not create an evaluation.
- An existing case's AI tab works only when its `transactionRef` has a matching
  eligible evaluation. Legacy cases without one show a helpful unavailable
  message.

## Use the frontend

1. Log in to the live console and open Transactions.
2. Enter the evaluated reference in **Phân tích theo mã giao dịch**, then select
   **Xem phân tích AI**. This route works even without a dashboard case.
3. For a matching case, opening its details and selecting **Phân tích AI** is
   another entry point.
4. If no analysis is saved, select **Phân tích bằng AI**. The backend calls
   OpenRouter, validates the response and saves it.
5. Read **Vì sao bị gắn cờ fraud?** and **Hướng điều tra**. Each section cites
   transaction evidence; recommendations include priority and data to collect.
6. Reopen the panel to read the saved result without a new model call.

Demo mode uses a clearly labeled local synthetic example. Selecting **Xem ví
dụ tổng hợp** never calls OpenRouter and does not demonstrate a live provider
connection. Use live mode for the end-to-end integration demo.

## Call the endpoints directly

Use the bearer access token from `/auth/login`, not the producer service
token. Replace the placeholders below with your token and an existing
evaluated fraud reference; the example reference is not automatically seeded.
Use URL-safe references such as `TX-DEMO-001`. Although the evaluation API's
existing contract accepts slash-containing references, encoded slashes are
blocked by the default security firewall on this path-based analysis route.
Such legacy references cannot be retrieved through these new endpoints.

```powershell
$analysisBase = 'http://localhost:8080/api/v1'
$analysisToken = '<access-token-from-login>'
$analysisRef = [Uri]::EscapeDataString('<evaluated-fraud-reference>')
$analysisHeaders = @{ Authorization = "Bearer $analysisToken" }

# Generate once or return the existing saved analysis; no JSON body required.
Invoke-RestMethod -Method Post -Uri "$analysisBase/transactions/$analysisRef/ai-analysis" -Headers $analysisHeaders

# Read the saved analysis without calling the model.
Invoke-RestMethod -Method Get -Uri "$analysisBase/transactions/$analysisRef/ai-analysis" -Headers $analysisHeaders
```

Both successful responses use the common envelope with
`data.fraudExplanation` and `data.investigationRecommendations`. See the
[API contract](api.md#ai-fraud-analysis) for the full schema and error table.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| `RESOURCE_NOT_FOUND` | Verify that the reference exists in evaluation history; a dashboard case alone is insufficient. |
| `AI_ANALYSIS_NOT_FOUND` | Use POST or the generation button once. |
| `AI_ANALYSIS_NOT_ELIGIBLE` | Check original `decision`, matched evidence and input size. |
| `AI_PROVIDER_UNAVAILABLE` | Set both backend key and model, then restart. |
| `AI_PROVIDER_ERROR` | Check model/schema support, key access, provider availability and whether returned evidence passed validation. |
| `AI_PROVIDER_TIMEOUT` | Retry generation or GET to check whether a prior request saved a result; reduce provider latency or adjust the timeout. |
| `UNAUTHORIZED` / `ACCESS_DENIED` | Log in again and use an `ADMIN` or `BANK_USER` token. |
| Model changed but analysis stayed the same | Expected: POST and GET return the original immutable cached analysis. |

## Validation limits

The server checks response structure, text/list bounds, unique priorities,
matched rule codes and permitted evidence references. All triggered rules with
positive score contributions must be covered in the explanation. It cannot prove that
every generated sentence follows the cited numbers. A specialist should
review the narrative and recommendations. AI does not change the original
fraud score or confirm fraud; review status remains a separate decision.

Same-reference requests serialize in one backend process. Multiple backend
instances can issue duplicate model requests concurrently; database
uniqueness preserves one saved result. No force-regeneration endpoint is
included in this demo.

## References

- [OpenRouter chat completions API](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request)
- [OpenRouter structured outputs](https://openrouter.ai/docs/features/structured-outputs)
- [Backend API contract](api.md#ai-fraud-analysis)

# Background transaction evaluation

The scanner reads successful transfers from PostgreSQL `transactions`, evaluates the
existing rule engine, appends `transaction_evaluations`, and creates a
`fraud_transactions` case only when `isFraud = true` (current threshold: 60).
All writes for one transfer commit or roll back together. Existing cases and their
review status are preserved. The two legacy POST APIs retain their existing behavior
and separate `risk_transfer_history` context.

## Rollout and configuration

1. Back up the database and deploy V5 through the existing Flyway startup mechanism.
   V1–V4 are unchanged. V5 supports a new database and adoption of a source table
   created with the original documented `transactions` schema; incompatible preexisting
   schemas must be reconciled before deployment. Do not manually execute V5 before Flyway.
2. Keep `APP_FRAUD_SCAN_ENABLED=false` (default) while loading and verifying source data.
3. Verify coverage using the trusted importer/operator workflow for
   `risk_account_coverage`. Never certify an interval with missing source transfers.
4. Test with representative transactions, then set `APP_FRAUD_SCAN_ENABLED=true` and
   restart/recreate the backend. For Compose, run
   `docker compose -f infra/docker-compose.yml up -d --force-recreate backend`.
5. Inspect evaluation outcomes and job logs. Disable the setting and restart to stop
   future scans; migrations, evaluation history, and cases remain in place.

Other settings:

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `APP_FRAUD_SCAN_FIXED_DELAY_MS` | `30000` | Delay after the previous scan finishes |
| `APP_FRAUD_SCAN_BATCH_SIZE` | `100` | Maximum transfers per scan, range 1–100 |

The first scan runs when scheduling starts. Reserve **at least two database pool
connections**: one dedicated session holds the advisory lock during a scan, while
processing uses another connection. Use a direct PostgreSQL connection or session
pooling; transaction-mode PgBouncer is incompatible with session advisory locks.
All backend instances targeting the source must use the same database and lock key.

## Source contract and context

Insert only successful transfers. Use `POST /api/v1/transactions` with
`X-Service-Token: <APP_SERVICE_TOKEN>` to enqueue a source transfer, or load the
source table through a trusted importer. No reset HTTP endpoint is added.
The caller supplies source identity, account labels/names, amount, currency,
`transaction_time` and optional actual `posted_at`. Store explicit timezone offsets.
Do not infer posting time from transfer time. PostgreSQL timestamps use microsecond
precision.

The POST API accepts camelCase names (`transactionTime`, `postedAt`, etc.) and
returns HTTP 201 with `transactionRef`, `processingStatus: "PENDING"`, and
`createdAt` in the common response envelope. It does not evaluate synchronously.
Duplicate references return 409 without changing the existing transfer; invalid
data returns 400. Supplied evaluation fields and other unknown fields are ignored.
The API validates the v1 limits below before inserting; SQL importers may insert
wider source values which the scanner will reject. Enable the scanner for queued
transfers to be evaluated.

`risk_score`, `risk_level`, `fraud_reason`, `rule_hits` and `transaction_chain` are
outputs; any initially supplied values are ignored by the engine. Defaults `0` and
`LOW` do not mean a pending transfer has been evaluated: inspect `processing_status`
and `evaluation_decision`.

V1 evaluates positive, whole VND amounts up to 999999999999999; references/account
IDs up to 80 characters; masked account labels up to 40; optional names up to 120.
Required identifiers/labels must not be blank. Sender and receiver must differ.
Transaction times cannot be in the future; posting time, when supplied, must fall
between transaction time and evaluation time. Unsupported records become `FAILED`
without silently truncating or rounding. Incompatible relevant source history also
fails evaluation instead of producing misleading scores.

Context comes from `transactions`, regardless of historical rows' processing status,
using the engine's existing time windows and source coverage certificates. Outbound
history is ordered by `(transaction_time, transaction_ref)`; at equal timestamps,
only references preceding the current reference enter context. Inbound history
uses actual posting times before the current transaction. There is no global
watermark rejection in this job, so old transactions can be backfilled. The legacy
API's watermark behavior is unchanged.

Missing coverage/posting data produces `PARTIAL` and possibly `INSUFFICIENT_DATA`;
it is not treated as a safe transfer. A partial evaluation that already reaches the
fraud threshold still creates a case. Source result `rule_hits` uses the existing
case rule-hit shape, including zero contributions for suppressed matched rules.
V1 case/source chains contain only the root transfer (`hopLevel=0`, `root=true`);
full rule evidence, features and coverage are stored in the evaluation audit.

Each successful run appends an immutable-by-convention evaluation record with the
source snapshot, result and audit. A database trigger prevents changes to original
source fields after the first successful evaluation, including `posted_at`.
Coverage and newly inserted historical transfers can change later evaluation results;
operators must explicitly reset affected transfers. Existing fraud cases are never
overwritten or removed by reevaluation, even if a new result is below threshold.

## Failure handling and manual reevaluation

Each successful or failed attempt increments `attempt_count`. Transient database or
transaction failures retry after 30 seconds, 2 minutes, then 10 minutes, with at most
four attempts. Permanent data failures become `FAILED` immediately. Failure status
is written in a fresh transaction after processing rollback. If even bookkeeping
fails, the row stays eligible and the job logs `UNRECORDED_ERROR`.
`PARTIAL` is a successful `COMPLETED` evaluation and is not retried automatically.

After fixing source data that has never been successfully evaluated, or certifying
additional history/coverage, reset selected rows explicitly:

```sql
UPDATE transactions
SET processing_status = 'PENDING',
    attempt_count = 0,
    next_attempt_at = NULL,
    last_error = NULL
WHERE transaction_ref IN ('REPLACE_WITH_TRANSACTION_REF');
```

Do not change original source fields of an evaluated transfer. A reset preserves
previous output fields until the new result commits; distinguish them by
`processing_status` and `evaluated_at`. The next evaluation appends history and
preserves any existing reviewed case.

Monitor `Transaction scan finished counts=...` and per-transfer status logs. Logs
and stored errors omit driver exception messages, secrets and full source payloads.
For operational inspection:

```sql
SELECT processing_status, evaluation_decision, count(*)
FROM transactions GROUP BY processing_status, evaluation_decision;

SELECT transaction_ref, attempt_count, next_attempt_at, last_error
FROM transactions WHERE processing_status IN ('RETRY', 'FAILED');

SELECT transaction_ref, evaluated_at, rule_version, result, audit
FROM transaction_evaluations
WHERE transaction_ref = 'REPLACE_WITH_TRANSACTION_REF'
ORDER BY evaluated_at DESC;
```

## Verification

Run the normal backend `qualityGate`. PostgreSQL-specific scanner integration tests
are enabled only when `SCAN_TEST_DATABASE_URL` is explicitly supplied. Use a
**disposable test database**, never a production connection: tests create and drop
random isolated schemas and exercise all migrations, triggers, JSONB, rollback,
retry, concurrent evaluation, advisory locks and JPA transaction participation.

```bash
export SCAN_TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/scan_test'
export SCAN_TEST_DATABASE_USER='postgres'
export SCAN_TEST_DATABASE_PASSWORD='your-test-password'
cd backend
./gradlew qualityGate
```

Without this variable, PostgreSQL tests are reported as skipped; normal regression
and lock-cleanup unit tests still run. To force a rerun after changing only test
environment settings, use `./gradlew :fraud:test --rerun-tasks`.

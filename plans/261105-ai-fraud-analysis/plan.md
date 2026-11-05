---
title: OpenRouter fraud analysis and investigation UI
status: completed
progress: 100%
priority: P1
effort: medium
created: 2026-11-05
---

# AI fraud analysis

## Overview

Implement the user-approved POST/GET analysis contract keyed by transactionRef.
Return fraudExplanation and investigationRecommendations from saved evaluation
evidence through OpenRouter; preserve existing evaluation and case contracts.

## Phases

- [x] Backend: provider client, grounded validation, persisted cache, authorization and migration.
- [x] Frontend: analysis panel in case detail and transaction-reference entry for evaluated transfers.
- [x] Configuration and docs: backend-only credentials, model and timeout; API and demo instructions.
- [x] Verification: mocked provider/service and UI tests, builds, lint, review and regression checks.

## Constraints and dependencies

Spring Boot 4 / Java 21, PostgreSQL, React/TypeScript; user-owned repository edits preserved.
External live inference needs a user-provided OpenRouter key and supported model.
No model training, autonomous review decisions or automatic fraud case creation.

## Success criteria

POST creates one reusable validated analysis for a saved FRAUD evaluation.
GET returns saved output without inference. Missing/non-fraud/provider failure states
are explicit. ADMIN/BANK_USER access only. FE renders both sections and supports retries.
Demo data is labeled; live mode never fabricates an analysis.

## Completion evidence

Backend full `qualityGate :app-bootstrap:bootJar` passed across all six modules.
120 tests passed with zero failures, errors or skipped tests: fraud 73, auth 42,
common 2, app-bootstrap 2 and database 1; identity has no test sources.
The application jar was built (69,947,796 bytes). Frontend tests passed 15/15;
typecheck, lint, formatting and production build passed. React Doctor reported
91/100 with no issues. Browser smoke covered desktop/mobile demo analysis and
live reference lookup. See [verification](verification.md),
[code review](code-review.md) and [completion report](reports/completion.md).

Live OpenRouter inference and production PostgreSQL verification were not run.
They require a configured key/model and prepared evaluated FRAUD data. Supported
model metadata was checked against OpenRouter's public catalog; this does not
constitute a live inference test. Known demo limits are documented in the guide.

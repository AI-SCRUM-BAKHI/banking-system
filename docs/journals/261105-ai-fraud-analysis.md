---
date: 2026-11-05
topic: ai-fraud-analysis
status: completed
---

# AI fraud analysis implementation

## Context

Demo requirement: explain existing fraud evaluations and suggest investigation
steps through OpenRouter, with API endpoints, frontend and environment setup.

## What happened

- Added GET/POST analysis by `transactionRef`, grounded in saved evaluation
  audit rather than dashboard case text alone.
- Added immutable PostgreSQL cache via Flyway V5, model/prompt metadata and
  input hash; provider failures remain retryable.
- Added case-detail AI tab and direct-reference lookup for evaluations that
  have no dashboard case. Demo examples remain local and explicitly synthetic.
- Added backend-only OpenRouter env configuration, provider/service tests,
  frontend behavior tests and setup/API documentation.

## Reflection

The existing separation between evaluations and investigation cases required
a direct-reference entry point. A case UUID cannot substitute for an
evaluation reference. Cached analysis makes reopening a demo deterministic.
Grounding checks validate references and shape, not every narrative claim.

## Decisions

- Use strict JSON schema output; configure the model through env.
- Keep original score, decision and review status unchanged.
- Do not auto-create cases or add regeneration in this scope.
- Retain existing user-owned changes and secrets.

## Verification and next steps

Frontend Vitest passed 7 files / 15 tests with `--maxWorkers=1`; targeted AI
panel/lookup/service checks passed 10 tests. Typecheck, lint and production
build passed. React Doctor reported 91/100 and no issues. Browser smoke
verified demo AI output on desktop/mobile and the live direct-reference modal.
Default parallel tests initially hit host memory limits; serial workers passed.

Backend full `qualityGate :app-bootstrap:bootJar` passed across six modules after
constraining worker/JVM resources to avoid host memory pressure. All 120 tests
passed without failures, errors or skipped tests: fraud 73, auth 42, common 2,
app-bootstrap 2 and database 1. Identity has no test sources. The application jar
was built successfully (69,947,796 bytes). Independent code review found no
remaining critical/high issue in this demo scope. The explanation must cover
every positive-contribution rule; a regression test checks omitted-rule rejection.

`openai/gpt-4o-mini` is provided as the example model after verifying its
`structured_outputs` capability in OpenRouter's public model catalog. No real
OpenRouter inference or production PostgreSQL run was performed. End-to-end
verification requires a configured key/model and prepared evaluated FRAUD data.
Known limits remain: URL-safe reference routing, possible duplicate provider
calls across backend instances and human review of narrative claims.

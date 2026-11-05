---
type: code-review
date: 2026-11-05
status: reviewed
---

# Code review: OpenRouter fraud analysis

## Summary

Independent static review found no remaining critical or high-severity issue
in the implemented demo scope. API, authorization, persistence, provider
failure handling, frontend integration and configuration match the approved
design. Test execution and build results are tracked by the tester.

## Findings and resolution

| Finding | Resolution |
| --- | --- |
| A response could omit the rules contributing to the FRAUD decision. | `AiAnalysisService.create` now requires every triggered rule with positive `scoreContribution` to appear in the validated reasons. The provider prompt states the same constraint; `rejectsExplanationThatOmitsTheRuleDrivingFraud` verifies rejection before persistence. |
| Slash-containing references accepted by the existing evaluation contract cannot traverse the new path-based analysis route. | Accepted demo limitation explicitly documented in `docs/api.md` and `docs/ai-fraud-analysis.md`. Use URL-safe references; existing evaluation contract stays unchanged. |
| Grounding checks could be mistaken for proof of all narrative claims. | Both docs clarify that structure, rule membership, evidence references and contributing-rule coverage are validated; generated prose and numerical statements still need review. |

## Verified design properties

- GET reads persisted analysis without calling the provider; POST reuses an existing result.
- POST eligibility checks the saved FRAUD decision and matched-rule evidence.
- Provider credentials remain backend-only; error responses do not expose upstream bodies or keys.
- OpenRouter output uses a strict schema; malformed, truncated and invalidly cited output fails before save.
- SQL uses bound parameters; V5 links the cache to the evaluated transaction.
- `ON CONFLICT DO NOTHING` preserves the first saved result and its model/hash/prompt metadata.
- The persistence test exercises V4/V5 structure, snapshot lookup, insert, cache immutability and the foreign key.
- ADMIN/BANK_USER JWT authorization is covered by real filter-chain tests; producer tokens do not grant analysis access.
- Frontend uses the common API envelope, explicit generation, persisted reads, missing-evaluation and provider-error states.
- Case detail and direct-reference lookup both reach the analysis panel; demo examples are labeled and avoid network inference.
- Existing evaluation and review state transitions remain unchanged.

## Remaining limitations

Live OpenRouter inference requires the user's configured key/model and was not
part of this static review. Across backend instances duplicate model requests
can occur, although database uniqueness preserves one stored result. The
documented URL-safe-reference limitation and human review of generated prose
remain intentional demo constraints.

## Next steps

Use the tester's final report for runtime/build verification. No further source
changes requested by this review.

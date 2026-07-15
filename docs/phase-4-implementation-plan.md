# Phase 4 Implementation Plan — Eligibility and Matching

Status: Accepted with Docker-dependent verification complete

## Baseline and constraint

- Phases 0–3 are accepted through V4.
- PostgreSQL/Testcontainers verification and the authenticated real-stack workflow passed after Docker was authorized.
- Matching may use only `VERIFIED` candidate facts. Unknown candidate or job evidence must stay unknown.

## Work order

| Step | Deliverable | Evidence |
|---|---|---|
| 1 | Match, feedback, and recomputation-queue schema | Forward-only V5 and constraints |
| 2 | Deterministic eligibility engine with blocker caps | Eligibility unit tests |
| 3 | Engineering and annotation scoring with verified fact citations | Scoring unit tests |
| 4 | Match persistence, feedback, recompute hooks, and APIs | Service/controller tests and compile |
| 5 | Match breakdown and unknown-question UI | Frontend tests, lint, build |
| 6 | Regression, integration, and security gate | Acceptance checklist and Docker evidence |

## Gate result

The deterministic matching contract is implemented through V5. Backend formatting and 24 unit
tests passed; frontend lint, 9 tests, and production build passed. The production build emitted a
non-failing initial-bundle budget warning. Final Maven verify passed 32 unit tests and 9
Testcontainers integration tests through Flyway V8; the real-stack matching workflow passed.

# Phase 3 Implementation Plan — Classification, Regions, and Search

Status: Accepted (2026-07-14)

## Baseline

- Phase 2 is accepted with V1–V3 migrations, 13 backend unit tests, 8 PostgreSQL integration tests, 8 frontend tests, lint, and production build passing.
- Phase 3 consumes canonical sanitized job text. It does not implement candidate matching, resume generation, or application tracking.
- Groq remains optional and server-side. Automated tests use deterministic fakes and never make model network calls.

## Dependency-ordered work

| Step | Deliverable | Exit evidence |
|---|---|---|
| 1 | Add analysis history, requirements, technologies, regions, AI runs, and user job state schema | PASS — clean V1–V4 PostgreSQL 17.5 migration |
| 2 | Add deterministic AI/annotation prefilter and strict remote/Morocco region classifier | PASS — rule tests cover ambiguity, restrictions, regions, noise, and injection |
| 3 | Expand provider-neutral AI contract and implement optional Groq structured-output adapter | PASS — disabled provider used in automated runs; conditional strict/fallback adapter compiles |
| 4 | Add typed job-analysis schema, local validator, safe prompt boundary, persistence, and reanalysis | PASS — typed validator, delimited prompt, repair retry, immutable history |
| 5 | Add paginated job search/detail, filters, sections, state actions, and evidence | PASS — PostgreSQL API integration coverage |
| 6 | Add responsive list/detail UI with distinct relevance, remote, evidence, confidence, save/hide/archive states | PASS — frontend test, lint, and production build |
| 7 | Run regression, security, Docker, and documentation gates | PASS — verify/package, scans, audit, Compose config, and image builds |

## Explicit exclusions

- No Phase 4 eligibility or candidate match scoring.
- No tailored resume generation.
- No application creation or submission automation.
- No live Groq call in automated validation.

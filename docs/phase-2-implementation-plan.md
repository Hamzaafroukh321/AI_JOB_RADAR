# Phase 2 Implementation Plan — Source Registry and Ingestion

Status: Accepted (2026-07-14)

## Baseline

- Phase 1 is accepted: its unit, PostgreSQL integration, frontend lint/test/build, migration, privacy, and scope gates pass.
- Phase 2 adds approved job discovery and canonical ingestion only. Classification, regional eligibility, matching, resumes, and applications remain out of scope.
- No live external source call is required for tests; connector contracts use stored fixtures and deterministic HTTP doubles.

## Dependency-ordered work

| Step | Deliverable | Exit evidence |
|---|---|---|
| 1 | Add source, fetch-run, immutable raw-stage, canonical job, occurrence, and location schema | PASS — V1–V3 clean Flyway migration in PostgreSQL 17.5 |
| 2 | Add source registry with terms state, non-secret configuration, enable/disable/test/fetch APIs | PASS — integration coverage rejects unapproved enablement and secret-like values |
| 3 | Implement stable connector SPI plus Greenhouse, Lever, and Adzuna adapters | PASS — nine stored fixtures across three adapters |
| 4 | Implement manual pasted-description import and safe URL metadata handling | PASS — API integration plus SSRF/URL unit tests |
| 5 | Add sanitization, normalization, checksums, source identity, canonical fingerprinting, and exact dedupe | PASS — deterministic unit and PostgreSQL integration tests |
| 6 | Add isolated fetch orchestration, structured run metrics, failure states, and scheduler | PASS — source boundary, daily idempotency key, opt-in scheduler; no expiry path exists |
| 7 | Add source-health/fetch-run/manual-import UI | PASS — component test, lint, and production build |
| 8 | Update OpenAPI, docs, security checks, and acceptance evidence | PASS — generated OpenAPI endpoint integration and repository scans |

## Security and compliance

- Connectors are limited to official APIs, documented feeds, and public ATS GET endpoints.
- Source configuration stores environment-variable references, never API secret values.
- Terms must be `APPROVED` before enabling or fetching a network connector.
- Imported HTML is allowlist-sanitized and normalized to bounded plain text.
- URL fetch policy blocks private, loopback, link-local, non-HTTP(S), and unsafe redirect targets.
- No connector implements login, application submission, CAPTCHA handling, proxy rotation, or browser impersonation.

## Rollback

- Migration is forward-only and canonical/raw records remain auditable.
- Disable any connector immediately through the registry without deleting historical jobs or fetch runs.
- Deploying prior application code leaves additive Phase 2 tables intact.

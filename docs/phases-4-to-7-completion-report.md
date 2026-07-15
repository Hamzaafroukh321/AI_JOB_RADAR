# Phases 4-7 Completion Report

Date: 2026-07-14  
Release recommendation: **GO for the tested local/container release candidate; conditional NO-GO for a public production deployment until platform TLS, secret-manager, and manual accessibility controls are verified.**

## Outcome

Phases 4-7 are implemented through Flyway V8. Matching, fact-grounded versioned PDF/DOCX resumes,
manual application tracking, operations controls, container hardening, and recovery were exercised on
a clean Docker-backed stack. The application never submits an application and contains no employer
login automation, CAPTCHA handling, credentialed scraping, or anti-bot evasion.

## Acceptance summary

| Phase | Result | Evidence |
|---|---|---|
| 4 Matching | PASS | Unit tests plus authenticated real-stack eligibility/match flow |
| 5 Tailored resumes | PASS | Approved fact-backed version rendered to PDF and DOCX on the real stack |
| 6 Applications | PASS | Manual Applied flow, locked resume, events, and CSV export on the real stack |
| 7 Hardening | PASS for repository-controlled gates | Clean images, recovery drill, scans, health checks, and operations tests |

## Final checks actually run

| Check | Result |
|---|---|
| Backend Spotless check | PASS |
| Backend unit tests | PASS - 32 tests |
| Maven verify / Testcontainers | PASS - 32 unit tests and 9 integration tests |
| Flyway V1-V8 on clean PostgreSQL 17.5 | PASS |
| Backend package | PASS |
| Frontend `npm ci` | PASS - 0 vulnerabilities |
| Frontend lint | PASS |
| Frontend tests | PASS - 14 tests in 10 files |
| Frontend production build | PASS with a 4.21 kB warning-budget overage |
| Frontend production audit | PASS - 0 vulnerabilities |
| Playwright manual Applied/reversal flow | PASS |
| Development Compose config/up/health | PASS |
| Production Compose config | PASS with temporary non-secret validation placeholders |
| Backend and frontend image builds | PASS |
| Trivy fixed HIGH/CRITICAL image scan | PASS - 0 backend OS/JAR and 0 frontend OS findings |
| Docker Scout scan | BLOCKED - requires Docker Hub authentication; Trivy supplied the scan gate |
| Authenticated profile-to-application API smoke | PASS |
| PostgreSQL backup/checksum/isolated restore | PASS - Flyway 8 and seeded rows verified |
| MinIO delete/restore/download checksum drill | PASS |
| Repository secret scan | PASS |
| Frontend server-secret bundle scan | PASS |
| Forbidden automation capability scan | PASS |
| Groq network request | NOT RUN - no secret was read or injected; deterministic disabled/failure paths are tested |

## Real-stack workflow evidence

The smoke test authenticated through the frontend proxy, loaded CSRF/profile state, imported 12
candidate facts (8 verified), imported and analyzed a worldwide AI Engineer role, produced an
ELIGIBLE match, generated and approved a resume, downloaded a 1,390-byte PDF and 2,784-byte DOCX,
listed five variants, opened and marked an application Applied with a locked resume, observed three
events, and exported CSV.

The recovery drill restored Flyway rank 8 with one user, one job, and one application. A MinIO object
deleted after backup was restored and its downloaded SHA-256 matched the original.

## Remaining external or low-severity items

1. The initial frontend bundle is 804.21 kB, 4.21 kB above its warning budget.
2. Public TLS termination, platform secret-manager injection, monitoring-network restrictions, and
   proxy header preservation must be verified in the target hosting platform.
3. Complete keyboard, screen-reader, and contrast checks against the eventual public deployment.
4. A live Groq request is optional and was not needed for acceptance; configure a rotated key only
   through the deployment secret manager when that integration is intentionally enabled.

## Release decision

All repository-controlled Docker, migration, integration, build, security, workflow, and recovery
gates passed. The running local Compose stack is a valid release candidate. Public production GO is
owned by the deployment checklist for the external controls above.

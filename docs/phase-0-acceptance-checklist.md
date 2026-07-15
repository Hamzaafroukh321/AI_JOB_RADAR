# Phase 0 Acceptance Checklist

Status values are updated only from observed evidence: `PENDING`, `PASS`, `FAIL`, or `BLOCKED`.

| Criterion | Status | Evidence |
|---|---|---|
| Repository structure is coherent and documented | PASS | Root application, `backend`, `frontend`, `docs`, `scripts`, and CI structure reviewed and documented in README/architecture docs |
| Backend starts successfully | PASS | Production backend image started under Compose and reached healthy state |
| Frontend starts successfully | PASS | Production nginx image started under Compose and returned HTTP 200 |
| PostgreSQL is reachable | PASS | Compose PostgreSQL service reached healthy state; backend and Testcontainers integration tests connected successfully |
| MinIO is reachable | PASS | Compose MinIO service reached healthy state; private `aijobradar` bucket initialized and integration health test passed |
| Empty-database Flyway migration succeeds | PASS | `mvnw verify` applied `V1__phase_0_identity_and_sessions.sql` to a clean Testcontainers PostgreSQL database |
| Secure development login works | PASS | Real Firefox smoke used the explicit development seed and established a server-side session |
| Protected endpoint rejects unauthenticated access | PASS | Integration test and browser route-guard smoke both observed rejection/redirect behavior |
| Authenticated frontend shell works | PASS | Real Firefox smoke reached the protected dashboard and rendered server-provided Phase 0 summary data |
| Liveness and readiness work | PASS | `/actuator/health/liveness` and `/actuator/health/readiness` returned `UP` in Compose smoke; integration tests cover probes |
| OpenAPI matches implemented endpoints | PASS | Integration test loaded generated OpenAPI and asserted the Phase 0 auth/dashboard paths |
| Docker Compose configuration is valid | PASS | `docker compose config --quiet` completed successfully |
| Backend format/style check passes | PASS | `mvnw.cmd spotless:check` completed successfully after formatting |
| Backend unit tests pass | PASS | `mvnw.cmd test` passed 3 unit tests |
| Backend integration tests pass | PASS | `mvnw.cmd verify` passed 3 unit and 5 Testcontainers integration tests |
| Frontend lint passes | PASS | `npm run lint` completed successfully |
| Frontend unit tests pass | PASS | `npm test -- --watch=false` passed 6 tests in 4 files |
| Frontend production build passes | PASS | `npm run build` completed successfully; initial bundle 777.42 kB raw / 119.22 kB estimated transfer |
| CI is syntactically aligned with local commands | PASS | Workflow mirrors validated commands; actionlint 1.7.7 completed successfully |
| `.env.example` is complete and `.env` is ignored | PASS | Compose consumed the example contract and `git check-ignore .env` confirmed the local file is ignored |
| No secret exists in source, generated files, logs, or frontend bundle | PASS | Repository script, frontend bundle scan, and Gitleaks 8.27.2 passed; npm audits reported 0 vulnerabilities |
| Required README, changelog, AGENTS, assumptions, checklist, ADRs, and operations docs exist | PASS | Required artifacts reviewed in the final documentation pass |
| No later-phase feature is falsely marked complete | PASS | Scope review found only Phase 0 identity/session, dashboard shell, health, storage readiness, and disabled AI boundaries |
| No automatic job application functionality exists | PASS | Source scope scan found no application automation, LinkedIn login, Easy Apply, CAPTCHA, scraping, or anti-bot implementation |
| Repository is left runnable | PASS | Backend/frontend packages and hardened images built; the complete Compose stack is running healthy |

## Additional security scan note

The initial local scan was interrupted by Docker Desktop resource pressure. After Docker was
authorized again, both rebuilt runtime images passed Trivy fixed HIGH/CRITICAL scans with zero
findings. The CI workflow retains the blocking scan for every release candidate.

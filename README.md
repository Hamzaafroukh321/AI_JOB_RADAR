# AI Job Radar

AI Job Radar is a private job-intelligence dashboard for discovering and evaluating AI opportunities and, in later phases, preparing fact-grounded resumes. It never automatically submits job applications: the user always applies on the original employer site.

## Current status

Phases 0–7 are implemented and pass the repository-controlled Docker gates: secure foundation, candidate truth,
approved-source ingestion, classification/search, eligibility matching, fact-grounded PDF/DOCX
resumes, manual application tracking, container hardening, and tested database/object recovery.
Public deployment remains conditional on platform TLS, secret-manager, monitoring, and manual
accessibility verification.

Read the authoritative [product and engineering specification](AI_JOB_RADAR_PRODUCT_AND_ENGINEERING_SPEC.md), [architecture](docs/architecture.md), [assumptions](docs/assumptions.md), and [ADRs](docs/adr/) before editing.

## Prerequisites

- Java 21
- Node.js 22.12+ (Node 22 line) and npm 10
- Docker with Compose v2
- Git

Maven is not required globally; use the committed wrapper.

## Quick start

```powershell
Copy-Item .env.example .env
# Replace all change-this-local-* values.
docker compose up -d postgres minio minio-init mailpit
```

For the simplest complete start, run `docker compose up -d --build`. For host development, export the values in `.env` into the backend process environment, run `.\mvnw.cmd spring-boot:run` from `backend`, then `npm ci` and `npm start` from `frontend`.

The seed user exists only when `APP_ENV=development`, `DEV_SEED_ENABLED=true`, and a local email/password are explicitly supplied. `DEV_SEED_PROFILE_ENABLED=true` adds the specification's resume-backed local candidate targets without overwriting user changes or filling unknown facts. No credential is compiled into the application or documented as a production default.

See [local development operations](docs/operations/local-development.md) for exact start, stop, reset, health, logs, and troubleshooting commands. See [production foundation](docs/operations/production-foundation.md) for the conceptual topology and its Phase 7 limitations.

## Validation

```powershell
cd backend
.\mvnw.cmd spotless:check
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd package

cd ..\frontend
npm ci
npm run lint
npm test -- --watch=false
npm run build
npm run audit:production

cd ..
docker compose config
docker compose build backend frontend
powershell -File scripts/check-no-secrets.ps1
powershell -File scripts/check-frontend-bundle.ps1
```

## Environment and security

`.env` is ignored. `.env.example` contains names and replaceable local-only placeholders. Inject production secrets at runtime. `GROQ_API_KEY` remains server-only. Source configuration stores environment-variable names, never key values. The browser build must never contain database, storage, encryption, source, or Groq secrets.

Production must disable development seeding, enable secure cookies/HTTPS, restrict CORS, protect infrastructure on private networks, and complete Phase 7 hardening before deployment is described as production-ready.

See [production operations](docs/operations.md), the
[security review](docs/security-best-practices-report.md), and the
[accessibility/performance review](docs/accessibility-and-performance-review.md).

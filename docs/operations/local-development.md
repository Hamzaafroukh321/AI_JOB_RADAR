# Local Development

## Prerequisites

Java 21, Node.js 22.12 or newer in the Node 22 line, npm 10, Docker Desktop/Engine with Compose v2, and Git.

## Start the host-development workflow

```powershell
Copy-Item .env.example .env
# Edit every change-this-local-* value before use.
docker compose up -d postgres minio minio-init mailpit

cd backend
.\mvnw.cmd spring-boot:run

cd ..\frontend
npm ci
npm start
```

Environment variables from `.env` are consumed automatically by Compose, but host Java does not load `.env`. Export the database, storage, and development seed variables into the backend process environment (or use the all-container option) before starting it.

When `DEV_SEED_PROFILE_ENABLED=true`, development startup idempotently fills the seed account with
the resume-backed Hamza Afroukh headline, supported role/skill targets, and Europe, Middle East, and
worldwide-remote regions. It only replaces the generic `Local Developer` name and empty profile or
preference fields. It does not overwrite user-edited values or infer salary, work authorization,
sponsorship, relocation, availability, language proficiency, or other unknown facts. Resume facts
still require explicit verification in the profile review before matching or tailored-resume use.

Open the UI at `http://localhost:4200`, MinIO console at `http://localhost:9001`, Mailpit at `http://localhost:8025`, and OpenAPI at `http://localhost:8080/swagger-ui.html`.

## All-container option

```powershell
docker compose up -d --build
docker compose ps
```

## Health and logs

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
docker compose logs -f backend
docker compose logs -f postgres minio mailpit
```

## Stop and reset

```powershell
docker compose down
```

Destructive: the following command permanently deletes the local database and object-store volumes. Verify the Compose project and back up anything important first.

```powershell
docker compose down --volumes
```

## Common failures

- `DATABASE_PASSWORD is required`: copy `.env.example` to `.env` and set local values.
- Readiness is `DOWN`: confirm PostgreSQL and MinIO are healthy and that `minio-init` completed successfully.
- Login is unavailable: verify `DEV_SEED_ENABLED=true` and explicit seed email/password are present only in development.
- CSRF 403: request `/api/v1/auth/csrf` first and send the cookie value as `X-XSRF-TOKEN`; Angular does this through its HTTP client.
- Port conflict: stop the conflicting local service or change the loopback port mapping.

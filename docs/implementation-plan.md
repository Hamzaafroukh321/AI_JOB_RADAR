# Phase 0 Implementation Plan

Status: Phase 0 implemented and acceptance evidence recorded  
Scope: Phase 0 only (repository and foundation)

## Baseline

- Existing work: the authoritative specification and master build prompt only.
- Repository state: no Git metadata or application code; the provided remote has no advertised branch.
- Available tools: Java 21, Node.js 22.16, npm 10.9, Docker 28.3, Docker Compose 2.39; no system Maven.
- New work: all Phase 0 foundation artifacts below.
- Deferred: resume/profile, job sources, ingestion, Groq calls, classification, matching, resume generation, and applications (Phases 1-7).

## Dependency-ordered work

| Step | Work | Specification trace | Exit evidence / validation |
|---|---|---|---|
| 1 | Establish repository instructions, assumptions, checklist, and ADRs | Sections 0, 36, 39, 40, 42 | Documentation review; `git status --short` |
| 2 | Scaffold Java 21 Spring Boot modular monolith with Maven Wrapper | Sections 19, 36 | `./mvnw spotless:check`, `./mvnw test`, `./mvnw verify`, `./mvnw package` |
| 3 | Add users, session authentication, CSRF/CORS, headers, throttling, typed APIs, Problem Details, correlation IDs, OpenAPI, and health | Sections 8.1, 21, 26, 27.3 | Unit/integration tests and authenticated API smoke |
| 4 | Add minimal forward-only Flyway schema and idempotent development seed | Sections 20, 36, 39.4 | Empty PostgreSQL migration integration test; seed tests |
| 5 | Add object-storage abstraction and MinIO readiness without upload features | Sections 19, 27.3, 36 | MinIO integration/health smoke |
| 6 | Add provider-neutral disabled AI interface/configuration with no network path | Sections 16, 36 | Startup and tests prove disabled behavior; source scan |
| 7 | Scaffold strict Angular/PrimeNG/PrimeFlex frontend with login, guard, session handling, shell, protected dashboard, and error states | Sections 19.4, 22, 36 | `npm ci`, `npm run lint`, `npm test -- --watch=false`, `npm run build` |
| 8 | Add PostgreSQL, MinIO, Mailpit Compose services and production Dockerfiles | Sections 29, 36 | `docker compose config`, service health, image builds |
| 9 | Add CI, dependency/secret checks, generated-artifact and frontend-bundle scans | Sections 26, 29.4, 36 | Workflow inspection, local scan scripts, dependency audits |
| 10 | Complete README, changelog, operations/security/architecture docs and evidence checklist | Sections 19, 26, 29, 36, 40 | Documentation review and full validation rerun |

## Security considerations

- Use server-managed HTTP-only session cookies; never browser-stored bearer tokens.
- Keep CSRF enabled, CORS allowlisted, errors generic, and authentication throttled.
- Seed credentials are supplied only by explicit development environment configuration; production seeding is rejected.
- Never send candidate data or any request to Groq in Phase 0; `GROQ_API_KEY` remains empty and server-only.
- Do not log credentials, request bodies, PII, authorization headers, or environment values.

## Rollback

- Source changes are additive in this empty repository and remain reviewable through Git once initialized.
- Database changes are forward-only. During development only, the named PostgreSQL volume may be deliberately reset using the documented destructive command.
- No user production data migration or external system mutation occurs in Phase 0.

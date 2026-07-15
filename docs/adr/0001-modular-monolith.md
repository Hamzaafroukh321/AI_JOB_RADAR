# ADR 0001: Modular Monolith

- Status: Accepted
- Date: 2026-07-14

## Context

AI Job Radar will eventually contain authentication, profile, jobs, AI, documents, and application workflows, but Phase 0 needs a maintainable foundation without distributed-system overhead.

## Decision

Use one Spring Boot deployable organized by feature under `com.aijobradar`, with explicit `api`, `application`, `domain`, and `infrastructure` boundaries where a feature needs them. Use one Angular application organized into `core`, `shared`, and lazy feature areas. Keep external integrations behind internal interfaces.

## Consequences

- Local transactions and deployment remain simple.
- Module boundaries must be tested and cyclic dependencies avoided.
- A scheduler or worker can later be split without changing domain contracts, but Phase 0 creates no microservices.


# ADR 0003: Local Infrastructure

- Status: Accepted
- Date: 2026-07-14

## Context

Phase 0 requires reproducible PostgreSQL, S3-compatible storage, and development mail while keeping production secrets and data outside images.

## Decision

Use Docker Compose services for PostgreSQL, MinIO, a one-shot MinIO bucket initializer, and Mailpit. Use named volumes, health-based dependency ordering, loopback-bound development ports, and environment-injected credentials. Provide multi-stage backend/frontend images running as non-root users.

## Consequences

- Docker is required for full integration and smoke tests.
- Development ports are intentionally reachable only from the local machine.
- Production topology must avoid publishing database and MinIO administration ports and will be finalized in Phase 7.


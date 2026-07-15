# ADR 0002: Server-side Session Authentication

- Status: Accepted
- Date: 2026-07-14

## Context

The application is a private browser dashboard. It needs secure login, logout, current-user discovery, CSRF protection, restrictive CORS, and an upgrade path beyond one process.

## Decision

Use Spring Security with BCrypt password hashes and PostgreSQL-backed Spring Session. The browser receives an HTTP-only, SameSite=Lax session cookie. CSRF uses a readable `XSRF-TOKEN` cookie paired with the `X-XSRF-TOKEN` header so Angular can participate without exposing the session identifier. Login attempts are rate-limited in memory for Phase 0; distributed rate limiting is deferred and documented.

Development account creation is opt-in and requires explicit environment credentials. Production startup rejects seed enablement. Registration remains disabled.

## Consequences

- Tokens never enter localStorage or JavaScript.
- State-changing requests require a CSRF token, including login and logout.
- PostgreSQL is part of readiness and session availability.
- In-memory throttling protects a single instance but must be replaced or shared before multi-instance production scaling.


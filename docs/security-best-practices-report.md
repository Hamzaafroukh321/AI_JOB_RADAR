# Security Best-Practices Review

## Executive summary

No known Critical or High finding remains after review. One unsafe external-navigation path was
remediated with an HTTP/HTTPS protocol allowlist and tests. Session cookies are HttpOnly and
SameSite, CSRF remains enabled, CORS is allowlisted, passwords use BCrypt cost 12, the frontend has
a restrictive CSP, and production disables API documentation.

The Java/Spring backend was reviewed using established framework practices because the selected
security skill provides direct reference guidance only for the TypeScript frontend.

## Remediated

### SEC-001 — External application URL protocol validation

- Rule ID: JS-URL-001
- Severity: High before remediation
- Location: frontend/src/app/core/security/external-url.policy.ts:1,
  frontend/src/app/features/jobs/job.service.ts:38
- Evidence: API-provided application URLs now pass requireExternalHttpUrl, which permits only
  http: and https: before window.open at job-detail.component.ts:121.
- Impact: Without this check, a stored script-bearing URL could execute in a navigation context.
- Fix: Implemented protocol validation and tests covering javascript: and data: rejection.
- False-positive notes: Backend ingestion also applies URL controls, but frontend defense-in-depth
  is retained because API results are untrusted.

## Low / operational

### SEC-002 — Runtime headers and secret-manager integration require deployment verification

- Rule ID: JS-CSP-001 / operations
- Severity: Low
- Location: frontend/nginx.conf:7, docker-compose.production.yml
- Evidence: CSP and security headers exist in Nginx; production configuration references
  environment secrets and does not embed values.
- Impact: A proxy could drop headers or an operator could source secrets insecurely.
- Fix: Verify headers at the public endpoint and inject values from the platform secret manager.
- Mitigation: Production images are read-only, no-new-privileges, resource-limited, and OpenAPI is
  disabled.
- False-positive notes: TLS and edge-secret configuration are outside this repository.

### SEC-003 — CSRF cookie is intentionally JavaScript-readable

- Rule ID: session/CSRF design
- Severity: Low
- Location: backend/src/main/java/com/aijobradar/common/security/SecurityConfiguration.java:63
- Evidence: Spring's XSRF cookie uses HttpOnly=false so Angular can echo it in X-XSRF-TOKEN; the
  session cookie itself is HttpOnly at line 54.
- Impact: XSS could read the CSRF token, but XSS already executes with same-origin authority.
- Fix: No change; retain CSP, avoid HTML sinks, and keep session identifiers HttpOnly.
- Mitigation: Strict CORS, SameSite=Lax, CSP, and Angular escaping.
- False-positive notes: This is the documented cookie-to-header CSRF pattern, not a session-token
  exposure.

## Verification status and external gaps

- Trivy fixed HIGH/CRITICAL scans passed with zero findings for the backend OS/JAR and frontend OS.
- Testcontainers integration, Flyway V1-V8, authenticated workflow, and isolated recovery passed.
- Runtime TLS, public proxy behavior, and platform secret-manager behavior still require verification
  in the selected hosting environment.

# Phase 7 Acceptance Checklist

| Criterion | Status | Evidence |
|---|---|---|
| Backup and guarded restore tooling exists | PASS | Host-client and container-client modes; checksum and explicit confirmation guards |
| Restore test succeeds | PASS | Isolated PostgreSQL restore reached Flyway 8 and MinIO object checksum matched |
| Security tests and secret scans pass | PASS | URL policy tests, secret scan, bundle scan |
| No critical dependency vulnerabilities | PASS | npm audit 0; Trivy fixed HIGH/CRITICAL scans report 0 for both runtime images |
| Source and AI failure alerts work | PASS | Threshold/escalation unit test and persistent monitor |
| Retention jobs are bounded and observable | PASS | V8 plus bounded scheduled redaction |
| Accessibility review completed | PASS | Review documents remaining deployed checks |
| Production deployment is documented/reproducible | PASS | Development and production Compose validate; images build and run healthy |
| Docker/production-container checks | PASS | Clean stack, builds, health checks, real workflow, and recovery drill executed |

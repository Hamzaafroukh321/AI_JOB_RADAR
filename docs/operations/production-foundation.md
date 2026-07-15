# Production Foundation

Phase 0 is not a production-complete deployment; production hardening, backups, alerting, retention, accessibility audit, and restore testing belong to Phase 7.

The intended topology is a TLS reverse proxy serving the Angular static application and forwarding `/api` to one Spring Boot modular monolith, backed by private PostgreSQL and S3-compatible storage. Database and MinIO administration ports must not be publicly exposed.

Runtime secrets must come from a deployment secret manager or protected environment injection. Production must set secure cookies, strict allowed origins, HTTPS/HSTS at the edge, disable development seeding, and normally disable Swagger UI. Never put Groq/source credentials into frontend variables, images, Compose files, or logs.

Use immutable versioned images, non-root runtime users, encrypted off-host backups, firewall rules, health-based rollout, log redaction, and a tested rollback to the prior image plus compatible forward-only database migrations. Do not roll back a database volume by deletion.


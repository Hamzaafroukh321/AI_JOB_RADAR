# Production Operations

## Release prerequisites

- Pin backend and frontend images to immutable tags or digests.
- Supply database, object-storage, and optional Groq values from the deployment secret manager.
- Keep DEV_SEED_ENABLED=false, SESSION_COOKIE_SECURE=true, and OPENAPI_ENABLED=false.
- Terminate TLS at a reviewed reverse proxy and expose only the frontend.
- Run the complete deployment, migration, smoke, security, and restore drill before GO.

## Deploy

1. Copy .env.production.example outside the repository and replace every placeholder.
2. Validate docker-compose.production.yml.
3. Pull the pinned images, start the stack, and wait for health checks.
4. Verify login, source refresh isolation, matching, resume download, and the manual Applied flow.
5. Verify /actuator/prometheus is reachable only by the monitoring network.

Development and production Compose validation, runtime image builds, local health checks, and the
authenticated smoke workflow passed on 2026-07-14. Repeat them for each release candidate.

## Backup

Run scripts/backup.ps1 with host `pg_dump` and a preconfigured MinIO Client alias, or use its
`PostgresContainer` and `ObjectStorageContainerImage` options on a Docker host. Containerized object
backup reads credentials from `ObjectStorageEnvFile`; it never accepts secret values as arguments.
The script writes a SHA-256 manifest.

## Restore drill

Restore only into an isolated target first. Run scripts/restore.ps1 with the manifest, isolated
target URL, and -ConfirmRestore. The same container-client options are available when host clients
are absent. Verify row counts, object downloads, login, an approved resume, and an application event
chain. Record duration and evidence. Production GO requires this drill.

## Monitoring and alerts

- Scrape radar_source_failures_recent and radar_ai_failures_recent.
- Page on an open CRITICAL operational_alerts row; ticket WARNING alerts.
- Alert on failed readiness, database capacity, backup age, and disk usage at the platform layer.
- Raw imported payloads are redacted in bounded batches after their retention timestamp.

## Rollback

Stop traffic, retain the failed database and objects for investigation, deploy the previous pinned
images, and restore the last verified backup only when forward correction is unsafe. Flyway
migrations are forward-only; never delete or rewrite migration files. Validate schema compatibility
before an image rollback.

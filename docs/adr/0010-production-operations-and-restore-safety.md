# ADR 0010: Production Operations and Restore Safety

Status: Accepted

## Decision

Backups use PostgreSQL custom format with a SHA-256 manifest and an optional separately configured
object-storage mirror. Restore is a separate destructive script that requires an explicit
confirmation switch, validates the manifest before touching the database, and restores into the
operator-selected target database.

Production configuration uses immutable application images, external secrets, health checks,
resource limits, and no development seed. Metrics expose aggregate operational states only; logs
must not contain candidate document text, job descriptions, credentials, or model prompts.

## Consequences

Operators can audit and rehearse restoration without embedding credentials in scripts. A release
cannot be declared production-ready until the restore drill and container checks actually run.

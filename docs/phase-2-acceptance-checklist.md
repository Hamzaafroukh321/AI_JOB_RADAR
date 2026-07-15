# Phase 2 Acceptance Checklist

| Criterion | Status | Evidence |
|---|---|---|
| Source registry enforces terms state and non-secret configuration | PASS | Terms constraint, service validation, and integration rejection tests |
| Greenhouse public GET connector passes contract fixtures | PASS | Normal, empty, and invalid-schema stored fixtures |
| Lever public GET connector passes contract fixtures | PASS | Normal, empty, and invalid-schema stored fixtures |
| One documented query API connector passes contract fixtures | PASS | Adzuna normal, empty, and invalid-schema stored fixtures; env references only |
| Manual pasted-description import works and retains original URL | PASS | Authenticated API integration imports two attempts idempotently |
| Raw staging is immutable and checksum/parser metadata are retained | PASS | V3 constraints plus two distinct raw records retained in integration test |
| Imported content is sanitized, bounded, and treated as untrusted | PASS | Jsoup allowlist/size boundary and normalizer unit tests |
| Daily/manual fetch inserts and updates jobs idempotently | PASS | Scheduled day-bucket idempotency key and source-identity update path |
| Exact duplicates do not create duplicate canonical jobs | PASS | Repeat manual import leaves one private canonical job |
| One failed source does not block successful sources | PASS | Per-source exception isolation in scheduled loop and structured failure runs |
| Failed or partial fetch does not mass-expire jobs | PASS | No expiry mutation exists; only observed occurrences/jobs are updated |
| Source freshness, counts, errors, and next run are visible | PASS | Sources UI shows success time, run status, counts, and safe errors |
| Scheduler and manual fetch use locking/idempotency controls | PASS | Unique idempotency key with conflict reuse; opt-in scheduled execution |
| No prohibited scraping, login, or application behavior exists | PASS | Public GET-only adapters and forbidden-behavior repository scan |
| Backend/frontend/integration/build/security checks pass | PASS | 13 unit, 8 integration, 8 frontend tests; lint/build and secret scans passed |
| Phase 3 has not started before this gate passes | PASS | Phase 2 gate completed before Phase 3 plan/work |

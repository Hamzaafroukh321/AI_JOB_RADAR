# ADR 0005: Approved Source Ingestion Boundary

Status: Accepted

## Context

Job discovery must provide useful coverage without credentialed scraping, terms violations, mass-expiration after failures, or connector-specific data leaking into canonical search.

## Decision

- Register every source with an explicit source type, terms-review state, parser version, schedule, priority, and enabled state.
- Permit network fetch only when the source is enabled and terms state is `APPROVED`.
- Implement connectors as GET-only adapters returning immutable `RawJobRecord` contracts.
- Keep credentials outside registry JSON; adapters resolve named server environment variables at runtime.
- Stage every accepted raw record with checksum and parser metadata before normalization.
- Deduplicate first by source identity, then by a deterministic canonical fingerprint; retain every source occurrence.
- Treat failed/partial runs as health evidence only. Missing records are never expired from an unsuccessful run.
- Keep manual pasted jobs user-owned while public-source canonical jobs remain global.

## Consequences

- Connectors can be disabled without deleting historical jobs.
- Phase 2 supports exact dedupe; fuzzy cross-source review remains a later enhancement.
- Test fixtures exercise parsing without live source calls or credentials.

# ADR 0004: Candidate Truth and Document Ingestion

Status: Accepted

## Context

Phase 1 needs to preserve original resumes, extract reviewable content, and prevent unverified claims from reaching later resume generation. Groq integration is not delivered until Phase 3 and must not be a prerequisite for local document ingestion.

## Decision

- Store immutable uploaded originals in private object storage under randomized user-scoped keys.
- Store relational document/resume metadata and ordered extraction blocks with page/paragraph provenance.
- Use deterministic PDF/DOCX extraction in Phase 1. Fact proposals remain reviewable and carry provenance.
- Model corrections append-only: an edit supersedes the prior fact instead of overwriting its history.
- Treat only `VERIFIED` facts as generation-authorized through a dedicated query boundary.
- Increment the candidate profile version whenever verified truth or matching preferences change.
- Import specification seed facts as `PROPOSED`, requiring explicit review before they become verified.

## Consequences

- Phase 1 works without an AI network call and remains testable offline.
- Binary documents and extracted PII require strict access control and retention handling.
- Later AI-assisted proposal extraction can implement the same provider-neutral proposal boundary without weakening truth rules.

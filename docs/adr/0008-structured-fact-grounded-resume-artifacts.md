# ADR 0008: Structured, Fact-Grounded Resume Artifacts

Status: Accepted

## Context

Tailored documents must remain useful while making unsupported candidate claims impossible.
Generated binary files also need reproducible versions and immutable applied artifacts.

## Decision

Store a structured resume content model whose every claim carries one or more verified candidate
fact IDs. Validate those IDs against the current user's verified-fact set before persistence or
rendering. Store append-only versions with a content hash. Approval locks a version; later changes
create a new version. Render PDF and DOCX from the validated structure on the server.

The initial planner is deterministic and offline. A future Groq planner may propose only structure
and phrasing, must use a versioned prompt, and must pass the identical fact validator.

## Consequences

Documents are auditable, reproducible, and safe when AI is disabled. A verified fact becoming stale
does not mutate an already approved version. Layout customization is intentionally constrained in
the MVP.

# Phase 5 Implementation Plan — Truth-Grounded Tailored Resumes

Status: Accepted with Docker-dependent verification complete

## Gate result

The fact validator, five deterministic variants, structured versions, HTML preview, selectable-text
PDF, DOCX, approval, lock policy, downloads, and evidence UI are implemented through V6. All 29
backend tests and 10 frontend tests passed at the phase gate; final regression, PostgreSQL migration,
Testcontainers integration, and authenticated PDF/DOCX workflow checks also passed.

## Constraints

- Only `VERIFIED` candidate facts may become resume claims.
- Job text is untrusted context and cannot create candidate facts.
- Groq remains optional; deterministic planning keeps the repository runnable without network calls.
- Docker-backed migration/integration and real-stack artifact checks passed.

## Work order

| Step | Deliverable | Evidence |
|---|---|---|
| 1 | Versioned resume/content/artifact schema | Forward-only V6 |
| 2 | Structured content model and fact validator | Unit tests |
| 3 | Five deterministic candidate variants | Generation tests/API |
| 4 | HTML preview, PDF, and DOCX renderers | Extractable-text document tests |
| 5 | Approval, locking, versions, and downloads | Service/API behavior |
| 6 | Resume workspace with evidence/diff presentation | Frontend lint/tests/build |
| 7 | Docker-backed acceptance gate | Phase checklist and real-stack evidence |

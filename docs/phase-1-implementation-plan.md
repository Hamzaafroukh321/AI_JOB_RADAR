# Phase 1 Implementation Plan — Resume and Profile

Status: Implemented and accepted on 2026-07-14

## Baseline

- Phase 0 is present and its non-Docker backend/frontend checks pass on 2026-07-14.
- Phase 1 is limited to resume ingestion, candidate truth, preferences, languages, authorizations, and profile versioning.
- Job sources, job classification, matching, tailored resumes, and application tracking remain out of scope.

## Dependency-ordered work

| Step | Deliverable | Exit evidence |
|---|---|---|
| 1 | Add forward-only profile, document, resume, provenance, and fact schema | Empty-database and upgrade Flyway integration tests |
| 2 | Extend private object storage and add upload validation, checksum, duplicate detection, and antivirus hook | Unit and MinIO integration tests |
| 3 | Extract PDF/DOCX text with page/paragraph provenance and deterministic section detection | PDF/DOCX extraction tests |
| 4 | Add master-resume versioning, activation, extraction preview, and candidate-seed import as proposals | API integration tests |
| 5 | Add candidate profile, preference, language, authorization, and append-only fact review APIs | User-scope, transition, and profile-version tests |
| 6 | Add accessible profile UI for upload, review, preferences, languages, and authorizations | Component tests and browser smoke |
| 7 | Update OpenAPI, docs, CI/security scans, and acceptance evidence | Full validation suite |

## Security and privacy

- Originals use randomized user-scoped object keys and are never publicly exposed.
- File size, extension, MIME signature, filename, checksum, and antivirus-hook result are validated before acceptance.
- Resume contents, contact details, object keys, and signed URLs are never logged.
- Every profile/document/fact query is scoped by authenticated `user_id`.
- Only `VERIFIED` facts are exposed by the generation-facing query boundary.

## Rollback

- Code and UI changes are additive.
- Flyway migrations are forward-only; rollback means deploying the prior compatible application while retaining the new tables.
- Uploaded originals are immutable; activation changes never delete historical resume versions.

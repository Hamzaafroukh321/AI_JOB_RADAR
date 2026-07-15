# Phase 1 Acceptance Checklist

Status values: `PENDING`, `PASS`, `FAIL`, `BLOCKED`.

| Criterion | Status | Evidence |
|---|---|---|
| PDF upload is validated, stored privately, and parsed | PASS | PDF extractor unit test and authenticated multipart integration test |
| DOCX upload is validated, stored privately, and parsed | PASS | DOCX extractor unit test and authenticated multipart integration test |
| Immutable original, checksum, and duplicate detection work | PASS | Private object-store assertion, SHA-256 metadata, and duplicate-upload 409 integration assertion |
| Page/paragraph extraction provenance is retained | PASS | PDF page and DOCX paragraph provenance asserted in unit/integration tests |
| Resume versions are preserved and one can be activated | PASS | Relational version history, partial unique active constraint, and activation API/UI |
| Candidate seed import produces reviewable proposals | PASS | Development-only idempotent import test; imported facts remain `PROPOSED` |
| User can verify, edit, reject, and request clarification for facts | PASS | Typed transition APIs and review UI; verify/edit integration path passed |
| Fact edits preserve superseded history | PASS | Integration test asserts old fact becomes `SUPERSEDED` and replacement cites `supersedesFactId` |
| Only verified facts cross the generation-facing query boundary | PASS | Dedicated `verifiedFactsForGeneration` service boundary and verified-status integration query |
| Profile, preferences, languages, and authorizations are user-scoped | PASS | Every SQL mutation/query includes authenticated `user_id`; foreign-user UUID returns 404 |
| Unknown language and authorization facts are never inferred | PASS | Empty defaults plus explicit user-created rows with `verifiedByUser=true` asserted |
| Profile version increments on truth/preference changes | PASS | Append-only profile snapshots and integration version-advance assertion |
| Extraction preview and accessible review UI work | PASS | Profile component renders preview/review actions and accessibility/truth copy; component test passed |
| PII and resume text are absent from logs and frontend bundles | PASS | No resume logging path; repository secret scan and frontend server-variable bundle scan passed |
| Backend/frontend/integration/build checks pass | PASS | 6 backend unit, 7 PostgreSQL integration, 7 frontend tests; lint and production build passed |
| Repository remains runnable and Phase 2 has not started | PASS | Phase 1 package/build gates passed before Phase 2 planning began |

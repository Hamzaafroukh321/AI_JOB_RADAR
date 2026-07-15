# Phase 3 Acceptance Checklist

| Criterion | Status | Evidence |
|---|---|---|
| Deterministic prefilter separates AI, annotation, and obvious noise | PASS | Role-signal, duty-signal, and false-positive tests |
| Optional Groq access is provider-neutral and server-only | PASS | Domain uses `LanguageModelProvider`; conditional server adapter only |
| Structured output is locally validated before persistence | PASS | Typed enum/confidence/evidence/cross-field validator and constraints |
| Analysis history is preserved on material job changes | PASS | Content hash, current uniqueness, and `superseded_at` history |
| Prompt-like job content is delimited and remains untrusted | PASS | Fixed system message plus untrusted-description delimiter |
| Prompt-injection tests pass | PASS | Injection cannot add AWS or enter the system message |
| Remote alone remains unknown and is excluded from strict worldwide | PASS | Dedicated rule and UI tests |
| Morocco, Europe, Middle East, worldwide, and annotation sections are deterministic | PASS | Location/region rule pack and strict region persistence |
| Job list is paginated, searchable, sortable, and filterable | PASS | PostgreSQL full-text/section integration and typed frontend filters |
| Job detail exposes source, analysis evidence, and confidence | PASS | Detail API/UI plus integration assertions |
| Save, hide, restore, and archive are user-scoped | PASS | User state table, visibility predicates, and integration coverage |
| Backend/frontend/integration/build/security gates pass | PASS | 20 unit, 9 integration, 9 frontend tests; build/scan gates pass |
| Phase 4 has not started | PASS | No eligibility engine or candidate match scoring exists |

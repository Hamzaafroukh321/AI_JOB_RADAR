# Changelog

All notable changes follow a Keep a Changelog-style structure.

## [Unreleased]

### Added

- Phase 0 repository foundation with Spring Boot, Angular, PostgreSQL, MinIO, Mailpit, Docker, and CI.
- Server-side session authentication, BCrypt passwords, CSRF/CORS controls, security headers, throttling, typed APIs, Problem Details, correlation IDs, OpenAPI, and health probes.
- Forward-only identity/session migration, opt-in idempotent development seed, accessible frontend shell, tests, ADRs, operations documentation, and secret/bundle checks.
- Phase 1 private PDF/DOCX resume ingestion, deterministic provenance-aware extraction, immutable original metadata, candidate fact review, append-only corrections, preferences, languages, authorizations, candidate seed proposals, and profile version history.
- Phase 2 approved-source registry, Greenhouse/Lever/Adzuna public connectors, immutable raw staging, canonical normalization and exact deduplication, private manual imports, isolated scheduled fetches, and source-health UI.
- Phase 3 deterministic AI/annotation and region classification, strict worldwide-remote semantics, validated optional Groq structured analysis, immutable analysis history, full-text job search/detail, evidence UI, and user-scoped save/hide/archive state.
- Phase 4 deterministic eligibility and candidate matching with verified-fact citations.
- Phase 5 versioned fact-grounded resume planning, HTML preview, PDF/DOCX rendering, approval,
  locking, evidence, and downloads.
- Phase 6 explicit manual application tracking, immutable events, reminders, CSV export, and
  confirmation flow.
- Phase 7 guarded backup/restore tooling, production Compose configuration, Prometheus metrics,
  alert thresholds, bounded retention, and operations/security/accessibility documentation.
- Explicit English/French master-resume labeling, correction of existing resume language metadata,
  upload feedback, source labels on proposed facts, and a visible review of every verified fact that
  can influence matching or generated resumes.
- Scheduled Jobicy public-API discovery packs for Europe, Middle East/EMEA, and worldwide remote,
  with deterministic region classification and strict worldwide eligibility.
- Idempotent local candidate personalization from the authoritative Hamza Afroukh resume seed,
  including role priorities, supported skills, and Europe, Middle East, and worldwide-remote targets
  while preserving unknown legal, salary, relocation, and availability fields.
- Forward-only correction of the Jobicy search tag to satisfy its documented public API constraint.
- Personal junior AI/software search defaults with functional experience and regional filters,
  visible seniority, pagination, streamlined navigation, and a redesigned evidence-first job list.
- Approved Remotive Artificial Intelligence public-API ingestion with source attribution and a
  forward-only correction for false internship classifications.
- Approved, paginated Arbeitnow Europe public-API ingestion for broader employer ATS coverage.

### Security

- Groq is disabled with no Phase 0 network implementation; no automatic job-application capability exists.
- Groq remains server-only and disabled by default; structured responses require local validation and prompt-injection boundaries before persistence.
- External application URLs are restricted to HTTP/HTTPS before browser navigation.

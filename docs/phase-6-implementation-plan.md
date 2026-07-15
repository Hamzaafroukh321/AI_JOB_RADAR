# Phase 6 Implementation Plan — Manual Application Tracking

Status: Accepted with Docker-dependent verification complete

## Gate result

The manual open/apply/reverse flow, locked resume linkage, immutable event schema, notes, reminders,
CSV export, state UI, and browser journey are implemented through V7. All 31 backend tests and 12
frontend tests passed; lint and production build passed. Playwright completed the Applied and
reversal confirmations against the production frontend with intercepted APIs. Final PostgreSQL,
Testcontainers, and authenticated real-stack Applied/event/export checks also passed.

## Constraints

- Opening a job means opening the employer's public application URL; the product never submits it.
- Applied state always requires explicit confirmation and an approved resume version.
- Audit events are append-only and user scoped.
- Docker-backed migration/integration and real-stack workflow checks passed.

## Work order

| Step | Deliverable | Evidence |
|---|---|---|
| 1 | Application, immutable event, note, and reminder schema | Forward-only V7 |
| 2 | Explicit state machine and confirmation contract | Unit tests |
| 3 | Open, apply/unapply, notes, reminders, export APIs | Scoped service/controller |
| 4 | Table/Kanban-ready UI and confirmation dialog | Component tests |
| 5 | Complete browser flow with intercepted API | Playwright CLI evidence |
| 6 | Docker-backed acceptance gate | Phase checklist and real-stack evidence |

# Phase 6 Acceptance Checklist

| Criterion | Status | Evidence |
|---|---|---|
| Applied takes two clicks plus confirmation | PASS | Component tests and Playwright browser flow |
| Resume version used is recorded and locked | PASS | ApplicationService and browser assertion |
| Switching Applied off requires confirmation | PASS | Component tests and Playwright browser flow |
| Audit history is immutable | PASS | Append-only service plus V7 mutation-rejection trigger |
| Events, notes, reminders, and export work | PASS | Scoped service/controller contracts |
| Complete browser flow passes | PASS | Playwright CLI returned Applied-before-confirmation and final Opened |
| Backend/frontend non-Docker gates pass | PASS | 31 backend tests; 12 frontend tests; lint/build |
| Docker-dependent integration and migration | PASS | V7 constraints and authenticated Applied/event/export flow passed on PostgreSQL |

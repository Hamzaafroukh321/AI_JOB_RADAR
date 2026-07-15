# ADR 0006: Validated AI Analysis and Deterministic Regions

Status: Accepted

## Context

Job descriptions are attacker-controlled text. Model output can be malformed or overconfident, and “remote” must never be interpreted as worldwide without geographic evidence.

## Decision

- Run deterministic relevance and region rules before optional model analysis.
- Keep all hosted-model HTTP behind `LanguageModelProvider`; domain services depend only on typed provider results.
- Delimit sanitized descriptions as untrusted user content, never system instructions.
- Accept model analysis only after local typed schema, enum, confidence, and evidence validation.
- Persist immutable analysis versions and redacted AI-run metadata, not full prompts.
- Use deterministic remote/Morocco classification as the authoritative strict-section boundary. Model output may supply evidence but cannot weaken it.
- Keep a fully functional deterministic analysis path when Groq is disabled, unavailable, rate-limited, or invalid.

## Consequences

- Search and strict region sections remain available offline.
- AI failure never removes a job or fabricates eligibility.
- Phase 3 tests require no API key or network access.
- Match and candidate eligibility remain deferred to Phase 4.


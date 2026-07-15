# ADR 0009: Manual Application State and Immutable Events

Status: Accepted

## Decision

Application tracking is an explicit user workflow. Opening the external URL records an `OPENED`
event but performs no submission. Marking `APPLIED` requires a separate toggle and confirmation
that selects an approved tailored resume version; that version becomes locked. Every transition,
note, and reminder change appends an immutable event. Reversing Applied requires a new confirmed
transition and never erases history.

## Consequences

The application table is current state for efficient UI queries, while events are the audit source
of truth. There is deliberately no employer login, form filling, CAPTCHA handling, or automated
application code.

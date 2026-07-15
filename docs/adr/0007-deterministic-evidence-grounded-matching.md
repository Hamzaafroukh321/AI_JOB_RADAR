# ADR 0007: Deterministic Evidence-Grounded Matching

Status: Accepted

## Decision

- Eligibility and score computation are deterministic; AI analysis supplies extracted job evidence only.
- Only verified candidate facts can create positive match evidence.
- Hard blockers are evaluated before scoring and apply specification score caps.
- Missing evidence becomes an unknown or gap, never an inferred candidate capability.
- Engineering and annotation roles use separate weight profiles.
- Match rows are versioned by candidate profile version and job analysis ID; recomputation never mutates historical versions.


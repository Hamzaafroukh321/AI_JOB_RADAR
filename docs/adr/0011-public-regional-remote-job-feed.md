# ADR 0011: Public regional remote-job feed

## Status

Accepted - 2026-07-15

## Context

The source registry had Greenhouse, Lever, and credentialed Adzuna adapters but no enabled discovery
source in a fresh local environment. Manual imports do not satisfy continuous discovery. The product
contract calls for Europe, Middle East, and strict worldwide-remote sections using approved public
APIs or feeds.

## Decision

Add a Jobicy connector using its documented public remote-jobs JSON API and retain the source URL for
attribution and application navigation. Seed enabled, approved discovery sources for Europe, EMEA
(from which deterministic rules select Middle East postings), and unfiltered remote results. Fetch
at most 100 records per source on the existing bounded scheduler. Use the API-compliant
`artificial intelligence` tag and validate its documented 3-50 character length locally. Treat every description as
untrusted and pass it through the existing normalizer, prompt-injection boundary, deduplication, and
deterministic region classifier. A remote result appears in Worldwide Remote only when its content
explicitly establishes worldwide/Morocco/containing-region eligibility.

## Consequences

- Fresh installations can discover jobs without source credentials or manual injection.
- Jobicy availability and fair-use limits become an external dependency isolated at the source-run
  boundary.
- EMEA results may include Europe and Africa; only jobs with explicit Middle East evidence appear in
  the Middle East section.
- Employer-side accuracy and vacancy status remain external; the app preserves source attribution and
  never submits applications.

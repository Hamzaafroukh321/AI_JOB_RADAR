# ADR 0013: Recall-first personal job coverage

## Status

Accepted - 2026-07-15

## Context

ADR 0012 made the personal search precise, but requiring both title relevance, an AI classification,
and explicit junior evidence left too few usable results. The user is interested in both AI and
software-engineering roles and explicitly prefers broader coverage as long as senior leadership and
unrelated commercial roles stay excluded.

## Decision

Supersede ADR 0012's default AI-score and explicit-junior requirements. The default personal search
uses title-level AI/software relevance, excludes explicit senior or founding roles, and excludes
unrelated commercial titles. Keep stricter junior/entry-signal and classified-junior filters as
explicit choices. The “All regions” option sends no region code; it must not silently apply the
`ALL_AI` classification region.

Increase the documented Arbeitnow Europe API fetch from five to twenty pages and add Remote OK's
published JSON API as an attributed worldwide-remote source. Add the public Lever postings feeds for
Jobgether and Spotify, and recognize adjacent software role titles such as frontend, platform,
DevOps, data engineering/science, computer vision, robotics, QA automation, cloud, and SRE. Continue
to retain the original source and application URLs. No authenticated scraping, browser automation,
CAPTCHA handling, or automated applications are introduced.

## Consequences

- The default result count grows while still rejecting explicit senior and unrelated titles.
- Some roles have unknown seniority; that uncertainty remains visible instead of being presented as
  verified junior status.
- Each fetch remains bounded, isolated, and auditable through source runs.
- External feed availability and terms remain operational dependencies.

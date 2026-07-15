# ADR 0012: Personal junior AI discovery defaults

## Status

Accepted - 2026-07-15

## Context

The single user is a junior software and AI engineer. Broad description-level AI matching admitted
senior, management, customer-service, sales, and marketing roles that were not useful. Several
navigation links also opened the same unfiltered jobs page, making the filters appear ineffective.
The user requested maximum coverage, including jobs commonly advertised on LinkedIn and Indeed.

## Decision

Make the default search explicitly personal: title-level AI/software relevance, medium-or-higher AI
signals, junior/entry evidence, and hard exclusions for senior leadership and unrelated business
titles. Keep broader non-senior and all-level searches available as deliberate filter choices. Add
Europe, Middle East/EMEA, Morocco, worldwide-from-Morocco, and AI-training sections to one coherent
search workspace with automatic select filtering, reset controls, pagination, and visible seniority.

Expand discovery only through documented public APIs, public employer ATS endpoints, approved feeds,
or user-provided content. Add Remotive's attributed public Artificial Intelligence API feed and
Arbeitnow's public Europe feed of aggregated ATS postings. Do not
scrape LinkedIn or Indeed, automate logins, handle CAPTCHAs, or evade anti-bot controls, even for a
single-user installation. Preserve the original source/application URL so applications remain manual.

## Consequences

- The default page is intentionally narrower and more useful than the total ingested corpus.
- Jobs lacking junior/entry evidence are hidden from the default but remain available through broader
  explicit filters.
- Public-feed coverage can grow without making the application dependent on brittle or prohibited
  scraping.
- Source attribution and rate limits remain mandatory.

# Security Notes

- BCrypt cost 12 protects development and future account password hashes.
- Server-side sessions use HTTP-only SameSite cookies; browser bearer tokens are not stored.
- CSRF tokens protect state-changing requests; CORS accepts only configured origins with credentials.
- Security headers exist at the backend and static frontend edge.
- Authentication errors are generic, validation uses Problem Details, and login attempts are throttled per client/account in one process.
- Production seeding is rejected. Groq remains disabled by default and is server-side only.
- Logs are metadata-only by convention: no request bodies, credentials, authorization headers, resume text, or PII.
- The in-memory login limiter is a Phase 0 single-instance foundation, not a multi-instance production control.

## AI analysis boundary

- Domain services use a provider-neutral interface and never call hosted-model endpoints directly.
- Sanitized job text is explicitly delimited as untrusted user content and never concatenated into the system message.
- Strict structured output may fall back only to JSON mode, then passes local typed schema and evidence validation. One repair attempt is allowed; invalid output is rejected.
- AI-run diagnostics store hashes, version/model metadata, latency, usage, and safe errors—not full prompts, keys, or candidate PII.
- Deterministic remote and Morocco rules remain authoritative; model output cannot broaden strict worldwide eligibility.

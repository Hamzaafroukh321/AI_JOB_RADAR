# Phase 0 Architecture

AI Job Radar is a Java 21 Spring Boot modular monolith with a strict Angular browser client. PostgreSQL owns user identity and sessions; MinIO provides the future private artifact boundary; Mailpit captures development-only mail. Browser authentication is server-side and cookie based.

Only identity/session, protected dashboard status, health, OpenAPI, storage readiness, and disabled AI-provider boundaries exist in Phase 0. No job, candidate profile, resume, Groq network, or application-tracking behavior is implemented.


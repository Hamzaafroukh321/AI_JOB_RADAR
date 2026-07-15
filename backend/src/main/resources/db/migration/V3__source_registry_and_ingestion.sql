CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE job_sources (
    id uuid PRIMARY KEY,
    source_key varchar(120) NOT NULL UNIQUE,
    display_name varchar(200) NOT NULL,
    source_type varchar(48) NOT NULL,
    regions_json jsonb NOT NULL DEFAULT '[]',
    categories_json jsonb NOT NULL DEFAULT '[]',
    base_url varchar(1000),
    terms_url varchar(1000),
    terms_review_status varchar(32) NOT NULL,
    credentials_required boolean NOT NULL DEFAULT false,
    enabled boolean NOT NULL DEFAULT false,
    schedule_cron varchar(120),
    timezone varchar(64) NOT NULL DEFAULT 'Africa/Casablanca',
    priority integer NOT NULL DEFAULT 100,
    configuration_encrypted jsonb NOT NULL DEFAULT '{}',
    parser_version varchar(40) NOT NULL,
    consecutive_failures integer NOT NULL DEFAULT 0,
    last_attempted_at timestamptz,
    last_successful_at timestamptz,
    next_scheduled_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_source_type CHECK (source_type IN ('GREENHOUSE', 'LEVER', 'ADZUNA', 'MANUAL')),
    CONSTRAINT ck_job_source_terms CHECK (terms_review_status IN ('APPROVED', 'REVIEW_REQUIRED', 'DISABLED')),
    CONSTRAINT ck_job_source_priority CHECK (priority BETWEEN 1 AND 1000),
    CONSTRAINT ck_job_source_enable_terms CHECK (enabled = false OR terms_review_status = 'APPROVED')
);
CREATE INDEX ix_job_sources_enabled_priority ON job_sources(enabled, priority);

CREATE TABLE fetch_runs (
    id uuid PRIMARY KEY,
    job_source_id uuid NOT NULL REFERENCES job_sources(id),
    trigger_type varchar(24) NOT NULL,
    idempotency_key varchar(240) NOT NULL UNIQUE,
    status varchar(32) NOT NULL,
    cursor_before varchar(1000),
    cursor_after varchar(1000),
    started_at timestamptz,
    finished_at timestamptz,
    http_call_count integer NOT NULL DEFAULT 0,
    records_received integer NOT NULL DEFAULT 0,
    records_inserted integer NOT NULL DEFAULT 0,
    records_updated integer NOT NULL DEFAULT 0,
    records_deduplicated integer NOT NULL DEFAULT 0,
    records_ignored integer NOT NULL DEFAULT 0,
    retry_count integer NOT NULL DEFAULT 0,
    error_category varchar(80),
    sanitized_error varchar(500),
    metrics_json jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_fetch_run_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'IMPORT')),
    CONSTRAINT ck_fetch_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'PARTIALLY_SUCCEEDED', 'SUCCEEDED', 'RATE_LIMITED', 'FAILED_RETRYABLE', 'FAILED_PERMANENT', 'CANCELLED'))
);
CREATE INDEX ix_fetch_runs_source_created ON fetch_runs(job_source_id, created_at DESC);
CREATE INDEX ix_fetch_runs_status_created ON fetch_runs(status, created_at DESC);

CREATE TABLE raw_job_records (
    id uuid PRIMARY KEY,
    fetch_run_id uuid NOT NULL REFERENCES fetch_runs(id),
    job_source_id uuid NOT NULL REFERENCES job_sources(id),
    submitted_by_user_id uuid REFERENCES app_users(id) ON DELETE SET NULL,
    external_id varchar(500) NOT NULL,
    source_url varchar(2000),
    application_url varchar(2000),
    payload_type varchar(32) NOT NULL,
    raw_payload text NOT NULL,
    content_hash char(64) NOT NULL,
    parser_version varchar(40) NOT NULL,
    parse_status varchar(32) NOT NULL,
    sanitization_status varchar(32) NOT NULL,
    parse_error_category varchar(80),
    created_at timestamptz NOT NULL,
    retention_delete_at timestamptz NOT NULL,
    CONSTRAINT uq_raw_job_per_run UNIQUE (fetch_run_id, external_id),
    CONSTRAINT ck_raw_job_parse CHECK (parse_status IN ('STAGED', 'NORMALIZED', 'INVALID', 'IGNORED')),
    CONSTRAINT ck_raw_job_sanitization CHECK (sanitization_status IN ('SANITIZED', 'PLAIN_TEXT', 'REJECTED'))
);
CREATE INDEX ix_raw_jobs_retention ON raw_job_records(retention_delete_at);
CREATE INDEX ix_raw_jobs_source_external ON raw_job_records(job_source_id, external_id);

CREATE TABLE jobs (
    id uuid PRIMARY KEY,
    owner_user_id uuid REFERENCES app_users(id) ON DELETE CASCADE,
    canonical_fingerprint char(64) NOT NULL,
    original_title varchar(500) NOT NULL,
    canonical_title varchar(500) NOT NULL,
    company_name varchar(300) NOT NULL,
    company_normalized varchar(300) NOT NULL,
    description_html text,
    description_text text NOT NULL,
    source_language varchar(16) NOT NULL DEFAULT 'und',
    source_posted_at timestamptz,
    source_updated_at timestamptz,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    last_verified_at timestamptz NOT NULL,
    expires_at timestamptz,
    closing_date date,
    source_status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    employment_type varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    seniority varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    workplace_mode varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    remote_scope varchar(48) NOT NULL DEFAULT 'UNKNOWN',
    morocco_remote_eligibility varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    salary_min numeric(14,2),
    salary_max numeric(14,2),
    salary_currency char(3),
    salary_period varchar(24),
    visa_sponsorship varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    primary_role_family varchar(80),
    ai_relevance varchar(16) NOT NULL DEFAULT 'UNCLASSIFIED',
    annotation_relevance varchar(16) NOT NULL DEFAULT 'UNCLASSIFIED',
    required_years_min numeric(4,1),
    required_years_max numeric(4,1),
    search_vector tsvector GENERATED ALWAYS AS (
      to_tsvector('simple', coalesce(canonical_title,'') || ' ' || coalesce(company_name,'') || ' ' || coalesce(description_text,''))
    ) STORED,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_source_status CHECK (source_status IN ('ACTIVE', 'EXPIRED', 'REMOVED', 'UNKNOWN')),
    CONSTRAINT ck_job_salary CHECK (salary_max IS NULL OR salary_min IS NULL OR salary_max >= salary_min)
);
CREATE UNIQUE INDEX uq_jobs_fingerprint_owner ON jobs(canonical_fingerprint, COALESCE(owner_user_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX ix_jobs_active_posted ON jobs(source_status, source_posted_at DESC);
CREATE INDEX ix_jobs_company_trgm ON jobs USING gin(company_normalized gin_trgm_ops);
CREATE INDEX ix_jobs_title_trgm ON jobs USING gin(canonical_title gin_trgm_ops);
CREATE INDEX ix_jobs_search ON jobs USING gin(search_vector);

CREATE TABLE job_source_occurrences (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    job_source_id uuid NOT NULL REFERENCES job_sources(id),
    submitted_by_user_id uuid REFERENCES app_users(id) ON DELETE SET NULL,
    external_id varchar(500) NOT NULL,
    source_url varchar(2000),
    application_url varchar(2000),
    source_posted_at timestamptz,
    source_updated_at timestamptz,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    active boolean NOT NULL DEFAULT true,
    raw_job_record_id uuid NOT NULL REFERENCES raw_job_records(id),
    duplicate_confidence varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_job_source_occurrence UNIQUE (job_source_id, external_id),
    CONSTRAINT ck_job_duplicate_confidence CHECK (duplicate_confidence IN ('EXACT', 'HIGH', 'MEDIUM', 'LOW', 'MANUAL'))
);
CREATE INDEX ix_job_occurrences_job ON job_source_occurrences(job_id, active);

CREATE TABLE job_locations (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    raw_location varchar(500) NOT NULL,
    city varchar(160),
    region varchar(160),
    country_code char(2),
    latitude numeric(9,6),
    longitude numeric(9,6),
    is_primary boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_job_location UNIQUE (job_id, raw_location)
);
CREATE INDEX ix_job_locations_country ON job_locations(country_code, job_id);

INSERT INTO job_sources(
    id, source_key, display_name, source_type, terms_review_status, credentials_required,
    enabled, priority, parser_version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000201', 'manual', 'Manual import', 'MANUAL',
    'APPROVED', false, true, 1, '1.0.0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE job_ai_analyses (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    job_content_hash char(64) NOT NULL,
    prompt_version varchar(40) NOT NULL,
    schema_version varchar(40) NOT NULL,
    model_id varchar(160) NOT NULL,
    analysis_json jsonb NOT NULL,
    validation_status varchar(24) NOT NULL,
    created_at timestamptz NOT NULL,
    superseded_at timestamptz,
    CONSTRAINT ck_job_analysis_validation CHECK (validation_status IN ('VALID','INVALID','DETERMINISTIC'))
);
CREATE UNIQUE INDEX uq_job_analysis_current_content ON job_ai_analyses(job_id, job_content_hash) WHERE superseded_at IS NULL;
CREATE INDEX ix_job_analysis_job_created ON job_ai_analyses(job_id, created_at DESC);

CREATE TABLE job_requirements (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    requirement_type varchar(32) NOT NULL,
    importance varchar(24) NOT NULL,
    normalized_value varchar(500) NOT NULL,
    display_text varchar(1000) NOT NULL,
    evidence_text varchar(1000) NOT NULL,
    confidence numeric(4,3) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_requirement_confidence CHECK (confidence BETWEEN 0 AND 1)
);
CREATE INDEX ix_job_requirements_job ON job_requirements(job_id, importance, sort_order);

CREATE TABLE job_technologies (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    normalized_name varchar(200) NOT NULL,
    display_name varchar(200) NOT NULL,
    importance varchar(24) NOT NULL,
    evidence_text varchar(1000),
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_job_technology UNIQUE(job_id, normalized_name)
);

CREATE TABLE job_regions (
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    region_code varchar(48) NOT NULL,
    classification_reason varchar(1000) NOT NULL,
    confidence numeric(4,3) NOT NULL,
    PRIMARY KEY(job_id, region_code),
    CONSTRAINT ck_job_region_confidence CHECK (confidence BETWEEN 0 AND 1)
);
CREATE INDEX ix_job_regions_region ON job_regions(region_code, job_id);

CREATE TABLE user_job_states (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    saved boolean NOT NULL DEFAULT false,
    hidden boolean NOT NULL DEFAULT false,
    archived boolean NOT NULL DEFAULT false,
    personal_tags_json jsonb NOT NULL DEFAULT '[]',
    note text,
    first_viewed_at timestamptz,
    last_viewed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_job_state UNIQUE(user_id, job_id)
);
CREATE INDEX ix_user_job_states_saved ON user_job_states(user_id, saved) WHERE saved=true;
CREATE INDEX ix_user_job_states_visibility ON user_job_states(user_id, hidden, archived);

CREATE TABLE ai_runs (
    id uuid PRIMARY KEY,
    user_id uuid REFERENCES app_users(id) ON DELETE SET NULL,
    task_type varchar(64) NOT NULL,
    provider varchar(32) NOT NULL,
    model_id varchar(160) NOT NULL,
    prompt_version varchar(40) NOT NULL,
    schema_version varchar(40) NOT NULL,
    input_hash char(64) NOT NULL,
    status varchar(32) NOT NULL,
    request_tokens integer,
    response_tokens integer,
    latency_ms bigint,
    retry_count integer NOT NULL DEFAULT 0,
    cached boolean NOT NULL DEFAULT false,
    error_category varchar(80),
    sanitized_error varchar(500),
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    CONSTRAINT ck_ai_run_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED_VALIDATION','FAILED_PROVIDER','RATE_LIMITED','DISABLED'))
);
CREATE INDEX ix_ai_runs_status_created ON ai_runs(status, created_at DESC);

CREATE INDEX ix_jobs_role_relevance ON jobs(primary_role_family, ai_relevance, annotation_relevance);
CREATE INDEX ix_jobs_remote ON jobs(workplace_mode, remote_scope, morocco_remote_eligibility);

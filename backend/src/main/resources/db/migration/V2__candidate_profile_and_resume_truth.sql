CREATE TABLE candidate_profiles (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    headline varchar(240),
    home_country_code char(2),
    home_region varchar(160),
    current_city varchar(160),
    relocation_preference varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    sponsorship_required boolean,
    active_master_resume_id uuid,
    profile_version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_profile_country CHECK (home_country_code IS NULL OR home_country_code = upper(home_country_code)),
    CONSTRAINT ck_candidate_profile_relocation CHECK (relocation_preference IN ('UNKNOWN', 'NO', 'DOMESTIC', 'INTERNATIONAL', 'REMOTE_ONLY'))
);

CREATE TABLE candidate_preferences (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    target_role_families_json jsonb NOT NULL DEFAULT '[]',
    target_seniority_json jsonb NOT NULL DEFAULT '[]',
    preferred_regions_json jsonb NOT NULL DEFAULT '[]',
    preferred_countries_json jsonb NOT NULL DEFAULT '[]',
    excluded_countries_json jsonb NOT NULL DEFAULT '[]',
    employment_types_json jsonb NOT NULL DEFAULT '[]',
    workplace_modes_json jsonb NOT NULL DEFAULT '[]',
    minimum_salary_json jsonb,
    contract_allowed boolean,
    freelance_allowed boolean,
    annotation_work_allowed boolean,
    temporary_work_allowed boolean,
    daily_digest_enabled boolean NOT NULL DEFAULT false,
    daily_digest_time time,
    minimum_match_score integer NOT NULL DEFAULT 60,
    freshness_days integer NOT NULL DEFAULT 14,
    excluded_companies_json jsonb NOT NULL DEFAULT '[]',
    excluded_keywords_json jsonb NOT NULL DEFAULT '[]',
    included_keywords_json jsonb NOT NULL DEFAULT '[]',
    working_hours_json jsonb,
    preferred_company_sizes_json jsonb NOT NULL DEFAULT '[]',
    preferred_industries_json jsonb NOT NULL DEFAULT '[]',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_preferences_score CHECK (minimum_match_score BETWEEN 0 AND 100),
    CONSTRAINT ck_candidate_preferences_freshness CHECK (freshness_days BETWEEN 1 AND 365)
);

CREATE TABLE candidate_authorizations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    country_code char(2) NOT NULL,
    authorization_status varchar(32) NOT NULL,
    sponsorship_needed boolean,
    expires_at date,
    verified_by_user boolean NOT NULL DEFAULT true,
    notes varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_candidate_authorization UNIQUE (user_id, country_code),
    CONSTRAINT ck_candidate_authorization_country CHECK (country_code = upper(country_code)),
    CONSTRAINT ck_candidate_authorization_status CHECK (authorization_status IN ('UNKNOWN', 'AUTHORIZED', 'NOT_AUTHORIZED', 'CITIZEN', 'PERMANENT_RESIDENT'))
);
CREATE INDEX ix_candidate_authorizations_user ON candidate_authorizations(user_id);

CREATE TABLE candidate_languages (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    language_code varchar(16) NOT NULL,
    spoken_level varchar(32) NOT NULL,
    written_level varchar(32) NOT NULL,
    professional_use boolean,
    verified_by_user boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_candidate_language UNIQUE (user_id, language_code),
    CONSTRAINT ck_candidate_language_spoken CHECK (spoken_level IN ('UNKNOWN', 'BASIC', 'CONVERSATIONAL', 'PROFESSIONAL', 'FLUENT', 'NATIVE')),
    CONSTRAINT ck_candidate_language_written CHECK (written_level IN ('UNKNOWN', 'BASIC', 'CONVERSATIONAL', 'PROFESSIONAL', 'FLUENT', 'NATIVE'))
);
CREATE INDEX ix_candidate_languages_user ON candidate_languages(user_id);

CREATE TABLE document_files (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    kind varchar(32) NOT NULL,
    original_filename varchar(255) NOT NULL,
    mime_type varchar(128) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 char(64) NOT NULL,
    storage_key varchar(512) NOT NULL UNIQUE,
    scan_status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT uq_document_user_hash_kind UNIQUE (user_id, sha256, kind),
    CONSTRAINT ck_document_size CHECK (size_bytes > 0),
    CONSTRAINT ck_document_kind CHECK (kind IN ('MASTER_RESUME')),
    CONSTRAINT ck_document_scan CHECK (scan_status IN ('CLEAN', 'REJECTED', 'NOT_CONFIGURED'))
);
CREATE INDEX ix_document_files_user_created ON document_files(user_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE master_resumes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    document_file_id uuid NOT NULL UNIQUE REFERENCES document_files(id),
    name varchar(160) NOT NULL,
    language_code varchar(16) NOT NULL DEFAULT 'en',
    extracted_text text,
    extraction_status varchar(32) NOT NULL,
    extraction_error_category varchar(64),
    active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_master_resume_extraction CHECK (extraction_status IN ('PENDING', 'EXTRACTED', 'FAILED'))
);
CREATE UNIQUE INDEX uq_master_resume_active_user ON master_resumes(user_id) WHERE active;
CREATE INDEX ix_master_resumes_user_created ON master_resumes(user_id, created_at DESC);

ALTER TABLE candidate_profiles
    ADD CONSTRAINT fk_candidate_profile_active_resume
    FOREIGN KEY (active_master_resume_id) REFERENCES master_resumes(id);

CREATE TABLE document_text_blocks (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    master_resume_id uuid NOT NULL REFERENCES master_resumes(id) ON DELETE CASCADE,
    page_number integer,
    paragraph_number integer NOT NULL,
    section_name varchar(80),
    text_content text NOT NULL,
    start_offset integer NOT NULL,
    end_offset integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_document_text_block UNIQUE (master_resume_id, paragraph_number),
    CONSTRAINT ck_document_text_page CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_document_text_offsets CHECK (start_offset >= 0 AND end_offset >= start_offset)
);
CREATE INDEX ix_document_text_blocks_resume ON document_text_blocks(user_id, master_resume_id, paragraph_number);

CREATE TABLE candidate_facts (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    master_resume_id uuid REFERENCES master_resumes(id),
    fact_type varchar(48) NOT NULL,
    organization varchar(200),
    role_title varchar(200),
    statement text NOT NULL,
    start_date date,
    end_date date,
    skills_json jsonb NOT NULL DEFAULT '[]',
    source_page integer,
    source_start_offset integer,
    source_end_offset integer,
    verification_status varchar(32) NOT NULL,
    user_edited boolean NOT NULL DEFAULT false,
    supersedes_fact_id uuid REFERENCES candidate_facts(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_fact_status CHECK (verification_status IN ('PROPOSED', 'VERIFIED', 'REJECTED', 'NEEDS_CLARIFICATION', 'SUPERSEDED')),
    CONSTRAINT ck_candidate_fact_source_offsets CHECK ((source_start_offset IS NULL AND source_end_offset IS NULL) OR (source_start_offset >= 0 AND source_end_offset >= source_start_offset)),
    CONSTRAINT ck_candidate_fact_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
CREATE INDEX ix_candidate_facts_user_status ON candidate_facts(user_id, verification_status, updated_at DESC);
CREATE INDEX ix_candidate_facts_resume ON candidate_facts(user_id, master_resume_id);

CREATE TABLE candidate_profile_versions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    profile_version bigint NOT NULL,
    reason varchar(80) NOT NULL,
    changed_entity_id uuid,
    snapshot_json jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_candidate_profile_version UNIQUE (user_id, profile_version)
);
CREATE INDEX ix_candidate_profile_versions_user ON candidate_profile_versions(user_id, profile_version DESC);

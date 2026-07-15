CREATE TABLE job_matches (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    candidate_profile_version bigint NOT NULL,
    job_analysis_id uuid NOT NULL REFERENCES job_ai_analyses(id),
    eligibility_state varchar(32) NOT NULL,
    overall_score integer NOT NULL,
    confidence numeric(4,3) NOT NULL,
    component_scores_json jsonb NOT NULL,
    strong_matches_json jsonb NOT NULL,
    partial_matches_json jsonb NOT NULL,
    missing_requirements_json jsonb NOT NULL,
    unknowns_json jsonb NOT NULL,
    hard_blockers_json jsonb NOT NULL,
    recommended_action varchar(500) NOT NULL,
    one_sentence_rationale varchar(1000) NOT NULL,
    prompt_version varchar(40) NOT NULL,
    model_id varchar(160) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_job_match_version UNIQUE(user_id,job_id,candidate_profile_version,job_analysis_id),
    CONSTRAINT ck_match_score CHECK(overall_score BETWEEN 0 AND 100),
    CONSTRAINT ck_match_confidence CHECK(confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_match_eligibility CHECK(eligibility_state IN ('ELIGIBLE','LIKELY_ELIGIBLE','NEEDS_REVIEW','LIKELY_INELIGIBLE','INELIGIBLE'))
);
CREATE INDEX ix_job_matches_user_score ON job_matches(user_id,overall_score DESC,created_at DESC);

CREATE TABLE match_feedback (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    job_match_id uuid REFERENCES job_matches(id) ON DELETE SET NULL,
    feedback_type varchar(40) NOT NULL,
    note varchar(1000),
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_match_feedback_type CHECK(feedback_type IN ('ACCURATE','TOO_HIGH','TOO_LOW','WRONG_ELIGIBILITY','WRONG_REQUIREMENT','OTHER'))
);

CREATE TABLE match_recompute_queue (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id uuid REFERENCES jobs(id) ON DELETE CASCADE,
    reason varchar(48) NOT NULL,
    requested_at timestamptz NOT NULL,
    processed_at timestamptz,
    CONSTRAINT ck_recompute_reason CHECK(reason IN ('PROFILE_CHANGED','JOB_CHANGED','USER_REQUESTED'))
);
CREATE INDEX ix_match_recompute_pending ON match_recompute_queue(requested_at) WHERE processed_at IS NULL;

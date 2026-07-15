CREATE TABLE tailored_resumes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    variant varchar(40) NOT NULL,
    title varchar(180) NOT NULL,
    current_version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ck_tailored_resume_variant CHECK (variant IN
      ('AI_ENGINEER','GENAI_RAG','JAVA_ANGULAR','AI_TRAINING','REMOTE_CONTRACT')),
    CONSTRAINT uq_tailored_resume_job_variant UNIQUE (user_id, job_id, variant)
);
CREATE INDEX ix_tailored_resumes_user_updated ON tailored_resumes(user_id, updated_at DESC);

CREATE TABLE tailored_resume_versions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    tailored_resume_id uuid NOT NULL REFERENCES tailored_resumes(id) ON DELETE CASCADE,
    version_number integer NOT NULL,
    content_json jsonb NOT NULL,
    content_sha256 char(64) NOT NULL,
    status varchar(24) NOT NULL,
    prompt_version varchar(48) NOT NULL,
    model_id varchar(96) NOT NULL,
    approved_at timestamptz,
    locked_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_tailored_resume_version UNIQUE (tailored_resume_id, version_number),
    CONSTRAINT ck_tailored_resume_status CHECK (status IN ('DRAFT','APPROVED','LOCKED')),
    CONSTRAINT ck_tailored_resume_approval CHECK
      ((status='DRAFT' AND approved_at IS NULL AND locked_at IS NULL)
       OR (status='APPROVED' AND approved_at IS NOT NULL AND locked_at IS NULL)
       OR (status='LOCKED' AND approved_at IS NOT NULL AND locked_at IS NOT NULL))
);
CREATE INDEX ix_tailored_resume_versions_user_resume
  ON tailored_resume_versions(user_id, tailored_resume_id, version_number DESC);

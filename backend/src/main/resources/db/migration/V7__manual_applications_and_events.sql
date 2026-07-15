CREATE TABLE applications (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    job_id uuid NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    tailored_resume_version_id uuid REFERENCES tailored_resume_versions(id),
    state varchar(24) NOT NULL,
    applied_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_application_user_job UNIQUE (user_id, job_id),
    CONSTRAINT ck_application_state CHECK
      (state IN ('SAVED','OPENED','APPLIED','INTERVIEW','OFFER','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_application_applied_resume CHECK
      ((state='APPLIED' AND tailored_resume_version_id IS NOT NULL AND applied_at IS NOT NULL)
       OR state<>'APPLIED')
);
CREATE INDEX ix_applications_user_state ON applications(user_id, state, updated_at DESC);

CREATE TABLE application_events (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    application_id uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    event_type varchar(32) NOT NULL,
    from_state varchar(24),
    to_state varchar(24),
    note text,
    metadata_json jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL
);
CREATE INDEX ix_application_events_user_app
  ON application_events(user_id, application_id, created_at DESC);

CREATE TABLE application_reminders (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    application_id uuid NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    remind_at timestamptz NOT NULL,
    message varchar(500) NOT NULL,
    completed_at timestamptz,
    created_at timestamptz NOT NULL
);
CREATE INDEX ix_application_reminders_due
  ON application_reminders(user_id, remind_at) WHERE completed_at IS NULL;

CREATE FUNCTION reject_application_event_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'application_events are immutable';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER application_events_immutable
  BEFORE UPDATE OR DELETE ON application_events
  FOR EACH ROW EXECUTE FUNCTION reject_application_event_mutation();

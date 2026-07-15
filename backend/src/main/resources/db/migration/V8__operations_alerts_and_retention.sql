ALTER TABLE raw_job_records ADD COLUMN payload_redacted_at timestamptz;
CREATE INDEX ix_raw_jobs_redaction
  ON raw_job_records(retention_delete_at) WHERE payload_redacted_at IS NULL;

CREATE TABLE operational_alerts (
    id uuid PRIMARY KEY,
    alert_type varchar(48) NOT NULL,
    fingerprint varchar(160) NOT NULL UNIQUE,
    severity varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    summary varchar(300) NOT NULL,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    resolved_at timestamptz,
    occurrence_count bigint NOT NULL DEFAULT 1,
    CONSTRAINT ck_operational_alert_severity CHECK (severity IN ('WARNING','CRITICAL')),
    CONSTRAINT ck_operational_alert_status CHECK (status IN ('OPEN','RESOLVED'))
);
CREATE INDEX ix_operational_alerts_open ON operational_alerts(status, last_seen_at DESC);

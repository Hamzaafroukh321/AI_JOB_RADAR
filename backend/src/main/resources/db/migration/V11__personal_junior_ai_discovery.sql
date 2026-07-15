ALTER TABLE job_sources DROP CONSTRAINT ck_job_source_type;
ALTER TABLE job_sources ADD CONSTRAINT ck_job_source_type
  CHECK (source_type IN ('GREENHOUSE', 'LEVER', 'ADZUNA', 'JOBICY', 'REMOTIVE', 'MANUAL'));

INSERT INTO job_sources(
  id,source_key,display_name,source_type,regions_json,categories_json,base_url,terms_url,
  terms_review_status,credentials_required,enabled,schedule_cron,timezone,priority,
  configuration_encrypted,parser_version,created_at,updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000205','remotive-ai-remote','Remotive - Artificial Intelligence','REMOTIVE',
   '["WORLDWIDE_REMOTE"]','["ALL_AI"]','https://remotive.com','https://remotive.com/remote-jobs/api',
   'APPROVED',false,true,'0 15 8 * * *','Africa/Casablanca',23,
   '{"category":"artificial-intelligence","limit":"100"}','1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (source_key) DO NOTHING;

UPDATE jobs
SET seniority='UNKNOWN',updated_at=CURRENT_TIMESTAMP,version=version+1
WHERE seniority='INTERN'
  AND lower(original_title) !~ '(^|[^a-z])(intern|internship|trainee|apprentice)([^a-z]|$)'
  AND lower(description_text) !~ '(^|[^a-z])(internship|intern position)([^a-z]|$)';

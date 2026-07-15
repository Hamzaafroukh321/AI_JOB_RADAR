ALTER TABLE job_sources DROP CONSTRAINT ck_job_source_type;
ALTER TABLE job_sources ADD CONSTRAINT ck_job_source_type
  CHECK (source_type IN ('GREENHOUSE', 'LEVER', 'ADZUNA', 'JOBICY', 'REMOTIVE', 'ARBEITNOW', 'MANUAL'));

INSERT INTO job_sources(
  id,source_key,display_name,source_type,regions_json,categories_json,base_url,terms_url,
  terms_review_status,credentials_required,enabled,schedule_cron,timezone,priority,
  configuration_encrypted,parser_version,created_at,updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000206','arbeitnow-europe','Arbeitnow - Europe ATS Jobs','ARBEITNOW',
   '["EUROPE"]','["ALL_AI"]','https://www.arbeitnow.com','https://www.arbeitnow.com/blog/job-board-api',
   'APPROVED',false,true,'0 45 8 * * *','Africa/Casablanca',24,
   '{"pages":"5"}','1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (source_key) DO NOTHING;

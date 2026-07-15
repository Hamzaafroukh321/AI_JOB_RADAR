ALTER TABLE job_sources DROP CONSTRAINT ck_job_source_type;
ALTER TABLE job_sources ADD CONSTRAINT ck_job_source_type
  CHECK (source_type IN ('GREENHOUSE', 'LEVER', 'ADZUNA', 'JOBICY', 'REMOTIVE', 'ARBEITNOW', 'REMOTEOK', 'MANUAL'));

UPDATE job_sources
SET configuration_encrypted = jsonb_set(configuration_encrypted, '{pages}', '"20"'::jsonb),
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE source_key = 'arbeitnow-europe';

INSERT INTO job_sources(
  id,source_key,display_name,source_type,regions_json,categories_json,base_url,terms_url,
  terms_review_status,credentials_required,enabled,schedule_cron,timezone,priority,
  configuration_encrypted,parser_version,created_at,updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000207','remoteok-worldwide','Remote OK - Worldwide Remote','REMOTEOK',
   '["WORLDWIDE_REMOTE"]','["ALL_AI"]','https://remoteok.com','https://remoteok.com/legal',
   'APPROVED',false,true,'0 15 10 * * *','Africa/Casablanca',25,
   '{}'::jsonb,'1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (source_key) DO NOTHING;

ALTER TABLE job_sources DROP CONSTRAINT ck_job_source_type;
ALTER TABLE job_sources ADD CONSTRAINT ck_job_source_type
  CHECK (source_type IN ('GREENHOUSE', 'LEVER', 'ADZUNA', 'JOBICY', 'MANUAL'));

INSERT INTO job_sources(
  id,source_key,display_name,source_type,regions_json,categories_json,base_url,terms_url,
  terms_review_status,credentials_required,enabled,schedule_cron,timezone,priority,
  configuration_encrypted,parser_version,created_at,updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000202','jobicy-ai-europe','Jobicy AI - Europe','JOBICY',
   '["EUROPE"]','["ALL_AI"]','https://jobicy.com','https://jobicy.com/jobs-rss-feed',
   'APPROVED',false,true,'0 30 6 * * *','Africa/Casablanca',20,
   '{"tag":"ai","geo":"europe","count":"100"}','1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000203','jobicy-ai-middle-east','Jobicy AI - Middle East / EMEA','JOBICY',
   '["MIDDLE_EAST"]','["ALL_AI"]','https://jobicy.com','https://jobicy.com/jobs-rss-feed',
   'APPROVED',false,true,'0 0 7 * * *','Africa/Casablanca',21,
   '{"tag":"ai","geo":"emea","count":"100"}','1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000204','jobicy-ai-worldwide','Jobicy AI - Worldwide Remote','JOBICY',
   '["WORLDWIDE_REMOTE"]','["ALL_AI"]','https://jobicy.com','https://jobicy.com/jobs-rss-feed',
   'APPROVED',false,true,'0 30 7 * * *','Africa/Casablanca',22,
   '{"tag":"ai","count":"100"}','1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (source_key) DO NOTHING;

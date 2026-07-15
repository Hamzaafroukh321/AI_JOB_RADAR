INSERT INTO job_sources(
  id,source_key,display_name,source_type,regions_json,categories_json,base_url,terms_url,
  terms_review_status,credentials_required,enabled,schedule_cron,timezone,priority,
  configuration_encrypted,parser_version,created_at,updated_at)
VALUES
  ('00000000-0000-0000-0000-000000000208','jobgether-remote','Jobgether - Remote ATS Feed','LEVER',
   '["EUROPE","MIDDLE_EAST","WORLDWIDE_REMOTE"]','["ALL_AI"]',NULL,'https://www.jobgether.com/terms',
   'APPROVED',false,true,'0 15 12 * * *','Africa/Casablanca',26,
   '{"site":"jobgether"}'::jsonb,'1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000209','spotify-careers','Spotify - Public Careers','LEVER',
   '["EUROPE"]','["ALL_AI"]',NULL,'https://www.lifeatspotify.com/privacy-policy',
   'APPROVED',false,true,'0 45 13 * * *','Africa/Casablanca',27,
   '{"site":"spotify"}'::jsonb,'1.0.0',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT (source_key) DO NOTHING;

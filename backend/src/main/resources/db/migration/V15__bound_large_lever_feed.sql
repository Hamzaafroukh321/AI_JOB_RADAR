UPDATE job_sources
SET configuration_encrypted = jsonb_set(configuration_encrypted, '{limit}', '"400"'::jsonb),
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE source_key = 'jobgether-remote';

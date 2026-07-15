UPDATE job_sources
SET configuration_encrypted = jsonb_set(
      configuration_encrypted,
      '{tag}',
      '"artificial intelligence"'::jsonb),
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE source_type = 'JOBICY'
  AND configuration_encrypted ->> 'tag' = 'ai';

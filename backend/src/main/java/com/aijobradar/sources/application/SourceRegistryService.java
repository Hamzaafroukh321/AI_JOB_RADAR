package com.aijobradar.sources.application;

import com.aijobradar.sources.api.SourceModels.FetchRunView;
import com.aijobradar.sources.api.SourceModels.SourceInput;
import com.aijobradar.sources.api.SourceModels.SourceView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SourceRegistryService {
  private static final Set<String> TYPES =
      Set.of("GREENHOUSE", "LEVER", "ADZUNA", "JOBICY", "REMOTIVE", "ARBEITNOW");
  private static final Set<String> TERMS = Set.of("APPROVED", "REVIEW_REQUIRED", "DISABLED");
  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public SourceRegistryService(JdbcClient jdbc, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<SourceView> list() {
    return jdbc.sql(
            """
            SELECT s.*,
              (SELECT status FROM fetch_runs r WHERE r.job_source_id=s.id ORDER BY created_at DESC LIMIT 1) last_run_status,
              (SELECT count(*) FROM job_source_occurrences o WHERE o.job_source_id=s.id) total_jobs
            FROM job_sources s ORDER BY priority,display_name
            """)
        .query(this::source)
        .list();
  }

  @Transactional(readOnly = true)
  public SourceView get(UUID id) {
    return list().stream()
        .filter(source -> source.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
  }

  @Transactional
  public SourceView create(SourceInput input) {
    validate(input);
    UUID id = UUID.randomUUID();
    OffsetDateTime now = now();
    jdbc.sql(
            """
            INSERT INTO job_sources(id,source_key,display_name,source_type,base_url,terms_url,
              terms_review_status,credentials_required,enabled,schedule_cron,timezone,priority,
              configuration_encrypted,parser_version,created_at,updated_at)
            VALUES (:id,:key,:name,:type,:base,:termsUrl,:terms,:credentials,:enabled,:cron,:timezone,
              :priority,CAST(:configuration AS jsonb),:parser,:now,:now)
            """)
        .param("id", id)
        .param("key", input.key().trim().toLowerCase(Locale.ROOT))
        .param("name", input.displayName().trim())
        .param("type", upper(input.type()))
        .param("base", blank(input.baseUrl()))
        .param("termsUrl", blank(input.termsUrl()))
        .param("terms", upper(input.termsReviewStatus()))
        .param("credentials", input.credentialsRequired())
        .param("enabled", false)
        .param("cron", blank(input.scheduleCron()))
        .param("timezone", input.timezone().trim())
        .param("priority", input.priority())
        .param("configuration", write(input.configuration()))
        .param("parser", input.parserVersion().trim())
        .param("now", now)
        .update();
    return get(id);
  }

  @Transactional
  public SourceView setEnabled(UUID id, boolean enabled) {
    int changed =
        jdbc.sql(
                """
                UPDATE job_sources SET enabled=:enabled,updated_at=:now,version=version+1
                WHERE id=:id AND source_type <> 'MANUAL'
                  AND (:enabled=false OR terms_review_status='APPROVED')
                """)
            .param("enabled", enabled)
            .param("now", now())
            .param("id", id)
            .update();
    if (changed == 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Source is missing, manual, or its terms are not approved");
    return get(id);
  }

  @Transactional(readOnly = true)
  public List<FetchRunView> runs(UUID sourceId) {
    return jdbc.sql(
            """
            SELECT * FROM fetch_runs WHERE job_source_id=:sourceId ORDER BY created_at DESC LIMIT 100
            """)
        .param("sourceId", sourceId)
        .query(this::run)
        .list();
  }

  public JobSourceConnector.SourceConfiguration configuration(UUID id) {
    SourceView source = get(id);
    return new JobSourceConnector.SourceConfiguration(
        source.id(),
        source.key(),
        source.displayName(),
        source.type(),
        source.baseUrl(),
        source.parserVersion(),
        source.configuration());
  }

  private void validate(SourceInput input) {
    String type = upper(input.type());
    if (!TYPES.contains(type))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source type");
    if (!TERMS.contains(upper(input.termsReviewStatus())))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid terms review status");
    if (input.priority() < 1 || input.priority() > 1000)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Priority must be between 1 and 1000");
    input
        .configuration()
        .forEach(
            (key, value) -> {
              String lower = key.toLowerCase(Locale.ROOT);
              boolean sensitiveName =
                  lower.contains("secret")
                      || lower.contains("password")
                      || lower.equals("apikey")
                      || lower.equals("appkey");
              if (sensitiveName
                  || (lower.endsWith("env") && !value.matches("[A-Z][A-Z0-9_]{2,80}")))
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Store only valid environment-variable references, never credentials");
              if (value.startsWith("gsk_") || value.startsWith("sk-"))
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Credential values must not be stored");
            });
  }

  private SourceView source(ResultSet rs, int row) throws SQLException {
    return new SourceView(
        rs.getObject("id", UUID.class),
        rs.getString("source_key"),
        rs.getString("display_name"),
        rs.getString("source_type"),
        rs.getString("base_url"),
        rs.getString("terms_url"),
        rs.getString("terms_review_status"),
        rs.getBoolean("credentials_required"),
        rs.getBoolean("enabled"),
        rs.getString("schedule_cron"),
        rs.getString("timezone"),
        rs.getInt("priority"),
        rs.getString("parser_version"),
        read(rs.getString("configuration_encrypted")),
        rs.getInt("consecutive_failures"),
        rs.getObject("last_attempted_at", OffsetDateTime.class),
        rs.getObject("last_successful_at", OffsetDateTime.class),
        rs.getString("last_run_status"),
        rs.getLong("total_jobs"));
  }

  private FetchRunView run(ResultSet rs, int row) throws SQLException {
    return new FetchRunView(
        rs.getObject("id", UUID.class),
        rs.getObject("job_source_id", UUID.class),
        rs.getString("trigger_type"),
        rs.getString("status"),
        rs.getObject("started_at", OffsetDateTime.class),
        rs.getObject("finished_at", OffsetDateTime.class),
        rs.getInt("http_call_count"),
        rs.getInt("records_received"),
        rs.getInt("records_inserted"),
        rs.getInt("records_updated"),
        rs.getInt("records_deduplicated"),
        rs.getInt("records_ignored"),
        rs.getString("error_category"),
        rs.getString("sanitized_error"));
  }

  private Map<String, String> read(String value) {
    try {
      return json.readValue(value, STRING_MAP);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored source configuration is invalid", exception);
    }
  }

  private String write(Map<String, String> value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Invalid source configuration", exception);
    }
  }

  private String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}

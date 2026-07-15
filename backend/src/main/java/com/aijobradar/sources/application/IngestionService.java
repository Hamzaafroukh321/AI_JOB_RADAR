package com.aijobradar.sources.application;

import com.aijobradar.jobs.application.JobAnalysisService;
import com.aijobradar.jobs.application.JobContentNormalizer;
import com.aijobradar.jobs.application.JobContentNormalizer.NormalizedJob;
import com.aijobradar.sources.api.SourceModels.ManualImportInput;
import com.aijobradar.sources.api.SourceModels.ManualImportResult;
import com.aijobradar.sources.api.SourceModels.SourceView;
import com.aijobradar.sources.application.JobSourceConnector.FetchContext;
import com.aijobradar.sources.application.JobSourceConnector.FetchResult;
import com.aijobradar.sources.application.JobSourceConnector.RawJobRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class IngestionService {
  private static final UUID MANUAL_SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final int MAX_PAGES_PER_FETCH = 20;
  private final JdbcClient jdbc;
  private final SourceRegistryService registry;
  private final JobContentNormalizer normalizer;
  private final JobAnalysisService analyses;
  private final Map<String, JobSourceConnector> connectors;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final Clock clock;

  public IngestionService(
      JdbcClient jdbc,
      SourceRegistryService registry,
      JobContentNormalizer normalizer,
      JobAnalysisService analyses,
      List<JobSourceConnector> connectors,
      TransactionTemplate transactions,
      ObjectMapper json,
      Clock clock) {
    this.jdbc = jdbc;
    this.registry = registry;
    this.normalizer = normalizer;
    this.analyses = analyses;
    this.connectors =
        connectors.stream()
            .collect(Collectors.toUnmodifiableMap(JobSourceConnector::type, Function.identity()));
    this.transactions = transactions;
    this.json = json;
    this.clock = clock;
  }

  public UUID fetch(UUID sourceId, String triggerType) {
    SourceView source = registry.get(sourceId);
    if ("MANUAL".equals(source.type()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Manual source uses the import endpoint");
    if (!source.enabled() || !"APPROVED".equals(source.termsReviewStatus()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Source is not enabled with approved terms");
    JobSourceConnector connector = connectors.get(source.type());
    if (connector == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No connector is installed for this source");
    String trigger = "SCHEDULED".equals(triggerType) ? "SCHEDULED" : "MANUAL";
    String key =
        trigger.equals("SCHEDULED")
            ? sourceId + ":scheduled:" + clock.instant().truncatedTo(ChronoUnit.DAYS)
            : sourceId + ":manual:" + UUID.randomUUID();
    UUID runId = createRun(sourceId, trigger, key);
    try {
      FetchResult result =
          connector.fetch(
              new FetchContext(
                  runId,
                  source.lastSuccessfulAt() == null ? null : source.lastSuccessfulAt().toInstant(),
                  null,
                  MAX_PAGES_PER_FETCH),
              registry.configuration(sourceId));
      Counters counters =
          transactions.execute(status -> persist(runId, source, null, result.records()));
      finishSuccess(runId, sourceId, result, counters);
    } catch (UnsafeSourceException exception) {
      finishFailure(runId, sourceId, exception.category(), exception.getMessage());
    } catch (RuntimeException exception) {
      finishFailure(runId, sourceId, "UNEXPECTED", "Source ingestion failed safely");
    }
    return runId;
  }

  public void fetchAllScheduled() {
    for (SourceView source : registry.list()) {
      if (source.enabled() && !"MANUAL".equals(source.type())) {
        try {
          fetch(source.id(), "SCHEDULED");
        } catch (RuntimeException ignored) {
          // A source is an isolation boundary: one failure must not stop other sources.
        }
      }
    }
  }

  public ManualImportResult importManual(UUID userId, ManualImportInput input) {
    UUID runId = createRun(MANUAL_SOURCE, "IMPORT", MANUAL_SOURCE + ":import:" + UUID.randomUUID());
    String payload = write(input);
    String externalId =
        hash(
            userId
                + "\n"
                + input.title()
                + "\n"
                + input.company()
                + "\n"
                + input.description()
                + "\n"
                + input.sourceUrl());
    RawJobRecord raw =
        new RawJobRecord(
            externalId,
            input.sourceUrl(),
            input.applicationUrl(),
            input.title(),
            input.company(),
            input.location(),
            input.description(),
            payload,
            null,
            null,
            input.employmentType(),
            input.workplaceMode(),
            null);
    SourceView source = registry.get(MANUAL_SOURCE);
    Counters counters =
        transactions.execute(status -> persist(runId, source, userId, List.of(raw)));
    FetchResult result = FetchResult.success(List.of(raw), null, 0);
    finishSuccess(runId, MANUAL_SOURCE, result, counters);
    return new ManualImportResult(runId, counters.lastJobId(), counters.inserted() == 1);
  }

  private UUID createRun(UUID sourceId, String trigger, String key) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = now();
    int inserted =
        jdbc.sql(
                """
            INSERT INTO fetch_runs(id,job_source_id,trigger_type,idempotency_key,status,started_at,created_at)
            VALUES (:id,:sourceId,:trigger,:key,'RUNNING',:now,:now)
            ON CONFLICT(idempotency_key) DO NOTHING
            """)
            .param("id", id)
            .param("sourceId", sourceId)
            .param("trigger", trigger)
            .param("key", key)
            .param("now", now)
            .update();
    if (inserted == 0) {
      return jdbc.sql("SELECT id FROM fetch_runs WHERE idempotency_key=:key")
          .param("key", key)
          .query(UUID.class)
          .single();
    }
    jdbc.sql("UPDATE job_sources SET last_attempted_at=:now,updated_at=:now WHERE id=:id")
        .param("now", now)
        .param("id", sourceId)
        .update();
    return id;
  }

  private Counters persist(UUID runId, SourceView source, UUID owner, List<RawJobRecord> records) {
    int inserted = 0;
    int updated = 0;
    int deduplicated = 0;
    int ignored = 0;
    UUID lastJobId = null;
    for (RawJobRecord original : records) {
      RawJobRecord raw = companyFallback(original, source.displayName());
      UUID rawId = UUID.randomUUID();
      OffsetDateTime now = now();
      jdbc.sql(
              """
              INSERT INTO raw_job_records(id,fetch_run_id,job_source_id,submitted_by_user_id,external_id,
                source_url,application_url,payload_type,raw_payload,content_hash,parser_version,
                parse_status,sanitization_status,created_at,retention_delete_at)
              VALUES (:id,:runId,:sourceId,:owner,:externalId,:sourceUrl,:applicationUrl,'JSON',:payload,
                :hash,:parser,'STAGED','SANITIZED',:now,:retention)
              """)
          .param("id", rawId)
          .param("runId", runId)
          .param("sourceId", source.id())
          .param("owner", owner)
          .param("externalId", raw.externalId())
          .param("sourceUrl", raw.sourceUrl())
          .param("applicationUrl", raw.applicationUrl())
          .param("payload", raw.rawPayload() == null ? "{}" : raw.rawPayload())
          .param("hash", hash(raw.rawPayload() == null ? "" : raw.rawPayload()))
          .param("parser", source.parserVersion())
          .param("now", now)
          .param("retention", now.plusDays(90))
          .update();
      try {
        NormalizedJob job = normalizer.normalize(raw);
        UUID existingOccurrence =
            jdbc.sql(
                    "SELECT job_id FROM job_source_occurrences WHERE job_source_id=:sourceId AND external_id=:externalId")
                .param("sourceId", source.id())
                .param("externalId", raw.externalId())
                .query(UUID.class)
                .optional()
                .orElse(null);
        if (existingOccurrence != null) {
          lastJobId = existingOccurrence;
          updateJob(existingOccurrence, job, now);
          updateOccurrence(source.id(), raw, rawId, now);
          updated++;
        } else {
          UUID existingJob = findJob(job.fingerprint(), owner);
          boolean isNew = existingJob == null;
          lastJobId = isNew ? insertJob(owner, job, now) : existingJob;
          insertOccurrence(
              lastJobId, source.id(), owner, raw, rawId, now, owner == null ? "EXACT" : "MANUAL");
          if (isNew) inserted++;
          else deduplicated++;
        }
        insertLocation(lastJobId, job.rawLocation(), now);
        analyses.analyzeDeterministically(lastJobId);
        jdbc.sql("UPDATE raw_job_records SET parse_status='NORMALIZED' WHERE id=:id")
            .param("id", rawId)
            .update();
      } catch (IllegalArgumentException exception) {
        jdbc.sql(
                "UPDATE raw_job_records SET parse_status='INVALID',parse_error_category='VALIDATION' WHERE id=:id")
            .param("id", rawId)
            .update();
        ignored++;
      }
    }
    return new Counters(inserted, updated, deduplicated, ignored, lastJobId);
  }

  private UUID findJob(String fingerprint, UUID owner) {
    return jdbc.sql(
            "SELECT id FROM jobs WHERE canonical_fingerprint=:fingerprint AND owner_user_id IS NOT DISTINCT FROM :owner")
        .param("fingerprint", fingerprint)
        .param("owner", owner)
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  private UUID insertJob(UUID owner, NormalizedJob job, OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO jobs(id,owner_user_id,canonical_fingerprint,original_title,canonical_title,
              company_name,company_normalized,description_html,description_text,source_posted_at,
              source_updated_at,first_seen_at,last_seen_at,last_verified_at,employment_type,
              workplace_mode,salary_min,salary_max,salary_currency,salary_period,created_at,updated_at)
            VALUES (:id,:owner,:fingerprint,:originalTitle,:title,:company,:companyNormalized,:html,
              :description,:posted,:sourceUpdated,:now,:now,:now,:employment,:workplace,:salaryMin,
              :salaryMax,:currency,:period,:now,:now)
            """)
        .param("id", id)
        .param("owner", owner)
        .param("fingerprint", job.fingerprint())
        .param("originalTitle", job.originalTitle())
        .param("title", job.canonicalTitle())
        .param("company", job.companyName())
        .param("companyNormalized", job.companyNormalized())
        .param("html", job.descriptionHtml())
        .param("description", job.descriptionText())
        .param("posted", offset(job.sourcePostedAt()))
        .param("sourceUpdated", offset(job.sourceUpdatedAt()))
        .param("now", now)
        .param("employment", job.employmentType())
        .param("workplace", job.workplaceMode())
        .param("salaryMin", job.salary().min())
        .param("salaryMax", job.salary().max())
        .param("currency", job.salary().currency())
        .param("period", job.salary().period())
        .update();
    return id;
  }

  private void updateJob(UUID id, NormalizedJob job, OffsetDateTime now) {
    jdbc.sql(
            """
            UPDATE jobs SET original_title=:originalTitle,canonical_title=:title,company_name=:company,
              company_normalized=:companyNormalized,description_html=:html,description_text=:description,
              source_posted_at=COALESCE(:posted,source_posted_at),source_updated_at=:sourceUpdated,
              last_seen_at=:now,last_verified_at=:now,source_status='ACTIVE',employment_type=:employment,
              workplace_mode=:workplace,salary_min=:salaryMin,salary_max=:salaryMax,
              salary_currency=:currency,salary_period=:period,updated_at=:now,version=version+1 WHERE id=:id
            """)
        .param("id", id)
        .param("originalTitle", job.originalTitle())
        .param("title", job.canonicalTitle())
        .param("company", job.companyName())
        .param("companyNormalized", job.companyNormalized())
        .param("html", job.descriptionHtml())
        .param("description", job.descriptionText())
        .param("posted", offset(job.sourcePostedAt()))
        .param("sourceUpdated", offset(job.sourceUpdatedAt()))
        .param("now", now)
        .param("employment", job.employmentType())
        .param("workplace", job.workplaceMode())
        .param("salaryMin", job.salary().min())
        .param("salaryMax", job.salary().max())
        .param("currency", job.salary().currency())
        .param("period", job.salary().period())
        .update();
  }

  private void insertOccurrence(
      UUID jobId,
      UUID sourceId,
      UUID owner,
      RawJobRecord raw,
      UUID rawId,
      OffsetDateTime now,
      String confidence) {
    jdbc.sql(
            """
            INSERT INTO job_source_occurrences(id,job_id,job_source_id,submitted_by_user_id,external_id,
              source_url,application_url,source_posted_at,source_updated_at,first_seen_at,last_seen_at,
              raw_job_record_id,duplicate_confidence,created_at,updated_at)
            VALUES (:id,:jobId,:sourceId,:owner,:externalId,:sourceUrl,:applicationUrl,:posted,
              :sourceUpdated,:now,:now,:rawId,:confidence,:now,:now)
            """)
        .param("id", UUID.randomUUID())
        .param("jobId", jobId)
        .param("sourceId", sourceId)
        .param("owner", owner)
        .param("externalId", raw.externalId())
        .param("sourceUrl", raw.sourceUrl())
        .param("applicationUrl", raw.applicationUrl())
        .param("posted", offset(raw.sourcePostedAt()))
        .param("sourceUpdated", offset(raw.sourceUpdatedAt()))
        .param("now", now)
        .param("rawId", rawId)
        .param("confidence", confidence)
        .update();
  }

  private void updateOccurrence(UUID sourceId, RawJobRecord raw, UUID rawId, OffsetDateTime now) {
    jdbc.sql(
            """
            UPDATE job_source_occurrences SET source_url=:sourceUrl,application_url=:applicationUrl,
              source_posted_at=COALESCE(:posted,source_posted_at),source_updated_at=:sourceUpdated,
              last_seen_at=:now,active=true,raw_job_record_id=:rawId,updated_at=:now
            WHERE job_source_id=:sourceId AND external_id=:externalId
            """)
        .param("sourceUrl", raw.sourceUrl())
        .param("applicationUrl", raw.applicationUrl())
        .param("posted", offset(raw.sourcePostedAt()))
        .param("sourceUpdated", offset(raw.sourceUpdatedAt()))
        .param("now", now)
        .param("rawId", rawId)
        .param("sourceId", sourceId)
        .param("externalId", raw.externalId())
        .update();
  }

  private void insertLocation(UUID jobId, String location, OffsetDateTime now) {
    if (location == null || location.isBlank()) return;
    jdbc.sql(
            "INSERT INTO job_locations(id,job_id,raw_location,is_primary,created_at) VALUES (:id,:jobId,:location,true,:now) ON CONFLICT(job_id,raw_location) DO NOTHING")
        .param("id", UUID.randomUUID())
        .param("jobId", jobId)
        .param("location", location)
        .param("now", now)
        .update();
  }

  private void finishSuccess(UUID runId, UUID sourceId, FetchResult result, Counters counters) {
    OffsetDateTime now = now();
    String status = result.complete() ? "SUCCEEDED" : "PARTIALLY_SUCCEEDED";
    jdbc.sql(
            """
            UPDATE fetch_runs SET status=:status,cursor_after=:cursor,finished_at=:now,http_call_count=:calls,
              records_received=:received,records_inserted=:inserted,records_updated=:updated,
              records_deduplicated=:deduplicated,records_ignored=:ignored WHERE id=:id
            """)
        .param("status", status)
        .param("cursor", result.nextCursor())
        .param("now", now)
        .param("calls", result.httpCalls())
        .param("received", result.records().size())
        .param("inserted", counters.inserted())
        .param("updated", counters.updated())
        .param("deduplicated", counters.deduplicated())
        .param("ignored", counters.ignored())
        .param("id", runId)
        .update();
    jdbc.sql(
            "UPDATE job_sources SET last_successful_at=:now,consecutive_failures=0,updated_at=:now WHERE id=:id")
        .param("now", now)
        .param("id", sourceId)
        .update();
  }

  private void finishFailure(UUID runId, UUID sourceId, String category, String safeMessage) {
    OffsetDateTime now = now();
    String status = "RATE_LIMITED".equals(category) ? "RATE_LIMITED" : "FAILED_RETRYABLE";
    String message =
        safeMessage == null
            ? "Source ingestion failed"
            : safeMessage.substring(0, Math.min(500, safeMessage.length()));
    jdbc.sql(
            "UPDATE fetch_runs SET status=:status,finished_at=:now,error_category=:category,sanitized_error=:message WHERE id=:id")
        .param("status", status)
        .param("now", now)
        .param("category", category)
        .param("message", message)
        .param("id", runId)
        .update();
    jdbc.sql(
            "UPDATE job_sources SET consecutive_failures=consecutive_failures+1,updated_at=:now WHERE id=:id")
        .param("now", now)
        .param("id", sourceId)
        .update();
  }

  private RawJobRecord companyFallback(RawJobRecord raw, String company) {
    if (raw.rawCompany() != null && !raw.rawCompany().isBlank()) return raw;
    return new RawJobRecord(
        raw.externalId(),
        raw.sourceUrl(),
        raw.applicationUrl(),
        raw.rawTitle(),
        company,
        raw.rawLocation(),
        raw.rawDescription(),
        raw.rawPayload(),
        raw.sourcePostedAt(),
        raw.sourceUpdatedAt(),
        raw.employmentType(),
        raw.workplaceMode(),
        raw.nextCursorHint());
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Manual job cannot be serialized", exception);
    }
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private OffsetDateTime offset(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private record Counters(
      int inserted, int updated, int deduplicated, int ignored, UUID lastJobId) {}
}

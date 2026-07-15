package com.aijobradar.jobs.application;

import com.aijobradar.ai.application.LanguageModelProvider;
import com.aijobradar.ai.application.LanguageModelProvider.AiMessage;
import com.aijobradar.ai.application.LanguageModelProvider.AiRequestOptions;
import com.aijobradar.ai.application.LanguageModelProvider.AiTaskType;
import com.aijobradar.matching.application.MatchRecomputeQueue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobAnalysisService {
  private final JdbcClient jdbc;
  private final DeterministicJobAnalyzer deterministic;
  private final JobAnalysisValidator validator;
  private final JobAnalysisPromptFactory prompts;
  private final LanguageModelProvider provider;
  private final ObjectMapper json;
  private final Clock clock;
  private final MatchRecomputeQueue matchQueue;

  public JobAnalysisService(
      JdbcClient jdbc,
      DeterministicJobAnalyzer deterministic,
      JobAnalysisValidator validator,
      JobAnalysisPromptFactory prompts,
      LanguageModelProvider provider,
      ObjectMapper json,
      Clock clock,
      MatchRecomputeQueue matchQueue) {
    this.jdbc = jdbc;
    this.deterministic = deterministic;
    this.validator = validator;
    this.prompts = prompts;
    this.provider = provider;
    this.json = json;
    this.clock = clock;
    this.matchQueue = matchQueue;
  }

  public UUID analyzeDeterministically(UUID jobId) {
    JobText job = load(jobId);
    JobAnalysis analysis =
        deterministic.analyze(
            job.title(), job.analysisText(), job.employmentType(), job.workplaceMode());
    return persist(jobId, job, analysis, "deterministic-v1", "DETERMINISTIC");
  }

  @Transactional
  public UUID reanalyze(UUID jobId, UUID userId) {
    JobText job = loadVisible(jobId, userId);
    JobAnalysis baseline =
        deterministic.analyze(
            job.title(), job.analysisText(), job.employmentType(), job.workplaceMode());
    if (!provider.isEnabled()
        || ("NONE".equals(baseline.aiRelevance()) && "NONE".equals(baseline.annotationRelevance())))
      return persist(jobId, job, baseline, "deterministic-v1", "DETERMINISTIC");
    UUID runId = startRun(userId, job, "groq");
    var result =
        provider.generateStructured(
            AiTaskType.JOB_ANALYSIS,
            prompts.messages(job.analysisText()),
            prompts.schema(),
            JobAnalysis.class,
            new AiRequestOptions("FAST", 3000, 0.0));
    if (!result.success()) {
      finishRun(runId, result, "FAILED_PROVIDER");
      return persist(jobId, job, baseline, "deterministic-v1", "DETERMINISTIC");
    }
    JobAnalysis safe = preserveDeterministicRegion(result.value(), baseline);
    List<String> errors = validator.validate(safe);
    if (!errors.isEmpty()) {
      List<AiMessage> repairMessages =
          new java.util.ArrayList<>(prompts.messages(job.analysisText()));
      repairMessages.add(
          new AiMessage(
              "user",
              "The prior JSON failed local validation: "
                  + String.join("; ", errors)
                  + ". Return one corrected JSON object matching the schema. Do not add unsupported facts."));
      var repaired =
          provider.generateStructured(
              AiTaskType.JOB_ANALYSIS,
              repairMessages,
              prompts.schema(),
              JobAnalysis.class,
              new AiRequestOptions("FAST", 3000, 0.0));
      if (!repaired.success()) {
        finishRun(runId, repaired, "FAILED_VALIDATION");
        return persist(jobId, job, baseline, "deterministic-v1", "DETERMINISTIC");
      }
      safe = preserveDeterministicRegion(repaired.value(), baseline);
      if (!validator.validate(safe).isEmpty()) {
        finishRun(runId, repaired, "FAILED_VALIDATION");
        return persist(jobId, job, baseline, "deterministic-v1", "DETERMINISTIC");
      }
      result = repaired;
    }
    finishRun(runId, result, "SUCCEEDED");
    return persist(jobId, job, safe, result.modelId(), "VALID");
  }

  private UUID persist(
      UUID jobId, JobText job, JobAnalysis analysis, String model, String validation) {
    List<String> errors = validator.validate(analysis);
    if (!errors.isEmpty())
      throw new IllegalArgumentException("Job analysis is invalid: " + String.join(", ", errors));
    String contentHash = hash(job.title() + "\n" + job.analysisText());
    UUID current =
        jdbc.sql(
                "SELECT id FROM job_ai_analyses WHERE job_id=:jobId AND job_content_hash=:hash AND superseded_at IS NULL")
            .param("jobId", jobId)
            .param("hash", contentHash)
            .query(UUID.class)
            .optional()
            .orElse(null);
    if (current != null) return current;
    OffsetDateTime now = now();
    jdbc.sql(
            "UPDATE job_ai_analyses SET superseded_at=:now WHERE job_id=:jobId AND superseded_at IS NULL")
        .param("now", now)
        .param("jobId", jobId)
        .update();
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO job_ai_analyses(id,job_id,job_content_hash,prompt_version,schema_version,
              model_id,analysis_json,validation_status,created_at)
            VALUES (:id,:jobId,:hash,:prompt,:schema,:model,CAST(:analysis AS jsonb),:validation,:now)
            """)
        .param("id", id)
        .param("jobId", jobId)
        .param("hash", contentHash)
        .param("prompt", JobAnalysisPromptFactory.VERSION)
        .param("schema", JobAnalysisPromptFactory.SCHEMA_VERSION)
        .param("model", model)
        .param("analysis", write(analysis))
        .param("validation", validation)
        .param("now", now)
        .update();
    jdbc.sql(
            """
            UPDATE jobs SET primary_role_family=:role,ai_relevance=:ai,annotation_relevance=:annotation,
              seniority=:seniority,workplace_mode=:workplace,remote_scope=:remote,
              morocco_remote_eligibility=:morocco,visa_sponsorship=:visa,
              required_years_min=:yearsMin,required_years_max=:yearsMax,updated_at=:now,version=version+1
            WHERE id=:jobId
            """)
        .param("role", analysis.primaryRoleFamily())
        .param("ai", analysis.aiRelevance())
        .param("annotation", analysis.annotationRelevance())
        .param("seniority", analysis.seniority())
        .param("workplace", analysis.workplaceMode())
        .param("remote", analysis.remoteScope())
        .param("morocco", analysis.moroccoEligibility())
        .param("visa", analysis.visaSponsorship())
        .param("yearsMin", analysis.requiredYearsMin())
        .param("yearsMax", analysis.requiredYearsMax())
        .param("now", now)
        .param("jobId", jobId)
        .update();
    replaceDetails(jobId, analysis, now);
    matchQueue.jobChanged(jobId);
    return id;
  }

  private void replaceDetails(UUID jobId, JobAnalysis analysis, OffsetDateTime now) {
    jdbc.sql("DELETE FROM job_requirements WHERE job_id=:jobId").param("jobId", jobId).update();
    jdbc.sql("DELETE FROM job_technologies WHERE job_id=:jobId").param("jobId", jobId).update();
    jdbc.sql("DELETE FROM job_regions WHERE job_id=:jobId").param("jobId", jobId).update();
    int order = 0;
    for (JobAnalysis.Requirement item : analysis.mustHaveRequirements())
      insertRequirement(jobId, item, "MUST_HAVE", order++, now);
    for (JobAnalysis.Requirement item : analysis.niceToHaveRequirements())
      insertRequirement(jobId, item, "NICE_TO_HAVE", order++, now);
    for (String technology : analysis.technologies()) {
      jdbc.sql(
              "INSERT INTO job_technologies(id,job_id,normalized_name,display_name,importance,created_at) VALUES (:id,:jobId,:normalized,:display,'MENTIONED',:now) ON CONFLICT(job_id,normalized_name) DO NOTHING")
          .param("id", UUID.randomUUID())
          .param("jobId", jobId)
          .param("normalized", technology.toLowerCase(Locale.ROOT))
          .param("display", technology)
          .param("now", now)
          .update();
    }
    if (!"NONE".equals(analysis.aiRelevance()))
      insertRegion(
          jobId,
          "ALL_AI",
          "AI relevance is " + analysis.aiRelevance(),
          analysis.overallConfidence());
    if (!"NONE".equals(analysis.annotationRelevance()))
      insertRegion(
          jobId,
          "AI_TRAINING_DATA",
          "Annotation relevance is " + analysis.annotationRelevance(),
          analysis.overallConfidence());
    if ("WORLDWIDE".equals(analysis.remoteScope())
        || ("REMOTE".equals(analysis.workplaceMode())
            && ("ELIGIBLE".equals(analysis.moroccoEligibility())
                || "POSSIBLY_ELIGIBLE".equals(analysis.moroccoEligibility()))))
      insertRegion(
          jobId,
          "WORLDWIDE_REMOTE",
          "Remote scope explicitly permits Morocco or a containing region",
          analysis.overallConfidence());
    if ("ELIGIBLE".equals(analysis.moroccoEligibility()))
      insertRegion(
          jobId, "MOROCCO", "Posting explicitly supports Morocco", analysis.overallConfidence());
    for (String region : analysis.allowedRegions())
      insertRegion(jobId, region, "Explicit allowed region", analysis.overallConfidence());
  }

  private void insertRequirement(
      UUID jobId, JobAnalysis.Requirement item, String importance, int order, OffsetDateTime now) {
    jdbc.sql(
            "INSERT INTO job_requirements(id,job_id,requirement_type,importance,normalized_value,display_text,evidence_text,confidence,sort_order,created_at,updated_at) VALUES (:id,:jobId,:type,:importance,:normalized,:display,:evidence,:confidence,:sort,:now,:now)")
        .param("id", UUID.randomUUID())
        .param("jobId", jobId)
        .param("type", item.type())
        .param("importance", importance)
        .param("normalized", item.requirement().toLowerCase(Locale.ROOT))
        .param("display", item.requirement())
        .param("evidence", item.evidence())
        .param("confidence", item.confidence())
        .param("sort", order)
        .param("now", now)
        .update();
  }

  private void insertRegion(UUID jobId, String code, String reason, double confidence) {
    jdbc.sql(
            "INSERT INTO job_regions(job_id,region_code,classification_reason,confidence) VALUES (:jobId,:code,:reason,:confidence) ON CONFLICT(job_id,region_code) DO UPDATE SET classification_reason=EXCLUDED.classification_reason,confidence=EXCLUDED.confidence")
        .param("jobId", jobId)
        .param("code", code)
        .param("reason", reason)
        .param("confidence", confidence)
        .update();
  }

  private JobAnalysis preserveDeterministicRegion(JobAnalysis ai, JobAnalysis baseline) {
    return new JobAnalysis(
        ai.jobSummary(),
        ai.primaryRoleFamily(),
        ai.secondaryRoleFamilies(),
        ai.aiRelevance(),
        ai.annotationRelevance(),
        ai.seniority(),
        ai.employmentTypes(),
        baseline.workplaceMode(),
        baseline.remoteScope(),
        baseline.allowedCountries(),
        baseline.excludedCountries(),
        baseline.allowedRegions(),
        baseline.timezoneRequirements(),
        baseline.moroccoEligibility(),
        ai.responsibilities(),
        ai.mustHaveRequirements(),
        ai.niceToHaveRequirements(),
        ai.technologies(),
        ai.requiredLanguages(),
        ai.requiredYearsMin(),
        ai.requiredYearsMax(),
        ai.visaSponsorship(),
        ai.citizenshipRequirements(),
        ai.securityClearanceRequirements(),
        ai.warnings(),
        ai.overallConfidence());
  }

  private UUID startRun(UUID userId, JobText job, String providerName) {
    UUID id = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO ai_runs(id,user_id,task_type,provider,model_id,prompt_version,schema_version,input_hash,status,created_at) VALUES (:id,:userId,'JOB_ANALYSIS',:provider,'pending',:prompt,:schema,:hash,'RUNNING',:now)")
        .param("id", id)
        .param("userId", userId)
        .param("provider", providerName)
        .param("prompt", JobAnalysisPromptFactory.VERSION)
        .param("schema", JobAnalysisPromptFactory.SCHEMA_VERSION)
        .param("hash", hash(job.title() + "\n" + job.analysisText()))
        .param("now", now())
        .update();
    return id;
  }

  private void finishRun(UUID id, LanguageModelProvider.AiResult<?> result, String status) {
    jdbc.sql(
            "UPDATE ai_runs SET model_id=:model,status=:status,request_tokens=:requestTokens,response_tokens=:responseTokens,latency_ms=:latency,retry_count=:retries,error_category=:category,sanitized_error=:error,completed_at=:now WHERE id=:id")
        .param("model", result.modelId())
        .param("status", status)
        .param("requestTokens", result.requestTokens())
        .param("responseTokens", result.responseTokens())
        .param("latency", result.latencyMs())
        .param("retries", result.retries())
        .param("category", result.errorCategory())
        .param("error", result.safeError())
        .param("now", now())
        .param("id", id)
        .update();
  }

  private JobText load(UUID id) {
    return jdbc.sql(
            """
            SELECT original_title,description_text,employment_type,workplace_mode,owner_user_id,
              (SELECT raw_location FROM job_locations l WHERE l.job_id=jobs.id ORDER BY is_primary DESC LIMIT 1) location
            FROM jobs WHERE id=:id
            """)
        .param("id", id)
        .query(this::job)
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
  }

  private JobText loadVisible(UUID id, UUID userId) {
    JobText job = load(id);
    if (job.owner() != null && !job.owner().equals(userId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
    return job;
  }

  private JobText job(ResultSet rs, int row) throws SQLException {
    return new JobText(
        rs.getString("original_title"),
        rs.getString("description_text"),
        rs.getString("employment_type"),
        rs.getString("workplace_mode"),
        rs.getObject("owner_user_id", UUID.class),
        rs.getString("location"));
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Analysis cannot be serialized", exception);
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

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private record JobText(
      String title,
      String description,
      String employmentType,
      String workplaceMode,
      UUID owner,
      String location) {
    String analysisText() {
      return location == null || location.isBlank()
          ? description
          : description + "\nLocation: " + location;
    }
  }
}

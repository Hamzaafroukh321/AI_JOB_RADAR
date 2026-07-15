package com.aijobradar.jobs.application;

import com.aijobradar.jobs.api.JobModels.JobCard;
import com.aijobradar.jobs.api.JobModels.JobDetail;
import com.aijobradar.jobs.api.JobModels.JobPage;
import com.aijobradar.jobs.api.JobModels.JobState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
public class JobQueryService {
  private static final String SENIOR_TITLE_PATTERN =
      "(^|[^a-z])(senior|sr\\.?|staff|lead|principal|manager|director|head|architect|founding|vp|vice president|chief)([^a-z]|$)";
  private static final String TARGET_TITLE_PATTERN =
      "(^|[^a-z])(ai|artificial intelligence|machine learning|ml|llm|generative|genai|prompt|rag|software engineer|software developer|backend|front[ -]?end|full[ -]?stack|web developer|mobile developer|devops|platform engineer|data engineer|data scientist|computer vision|nlp|robotics|qa engineer|test automation|cloud engineer|site reliability|sre|java|spring|angular|python|data annotat|coding evaluator|ai trainer)([^a-z]|$)";
  private static final String UNRELATED_TITLE_PATTERN =
      "(^|[^a-z])(customer service|customer success|sales|marketing|marketer|advertising|account manager|project manager|projektleiter|finance|legal|counsel|insurance|human resources|recruiter|onboarding|auditor)([^a-z]|$)";
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public JobQueryService(JdbcClient jdbc, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public JobPage search(UUID userId, Search query) {
    int size = Math.min(Math.max(query.size(), 1), 100);
    int page = Math.max(query.page(), 0);
    String where = where(query);
    String from =
        " FROM jobs j LEFT JOIN user_job_states us ON us.job_id=j.id AND us.user_id=:userId ";
    var count = jdbc.sql("SELECT count(*)" + from + where).param("userId", userId);
    var select =
        jdbc.sql(
                """
            SELECT j.*,COALESCE(us.saved,false) saved,COALESCE(us.hidden,false) hidden,
              COALESCE(us.archived,false) archived,
              (SELECT raw_location FROM job_locations l WHERE l.job_id=j.id ORDER BY is_primary DESC LIMIT 1) location,
              (SELECT s.display_name FROM job_source_occurrences o JOIN job_sources s ON s.id=o.job_source_id WHERE o.job_id=j.id ORDER BY o.first_seen_at LIMIT 1) source
            """
                    + from
                    + where
                    + order(query.sort())
                    + " LIMIT :limit OFFSET :offset")
            .param("userId", userId)
            .param("limit", size)
            .param("offset", page * size);
    count = params(count, query, userId);
    select = params(select, query, userId);
    long total = count.query(Long.class).single();
    List<JobCard> content = select.query(this::card).list();
    return new JobPage(content, page, size, total, (int) Math.ceil((double) total / size));
  }

  @Transactional
  public JobDetail detail(UUID userId, UUID jobId) {
    JobCard card =
        jdbc.sql(
                """
            SELECT j.*,COALESCE(us.saved,false) saved,COALESCE(us.hidden,false) hidden,
              COALESCE(us.archived,false) archived,
              (SELECT raw_location FROM job_locations l WHERE l.job_id=j.id ORDER BY is_primary DESC LIMIT 1) location,
              (SELECT s.display_name FROM job_source_occurrences o JOIN job_sources s ON s.id=o.job_source_id WHERE o.job_id=j.id ORDER BY o.first_seen_at LIMIT 1) source
            FROM jobs j LEFT JOIN user_job_states us ON us.job_id=j.id AND us.user_id=:userId
            WHERE j.id=:jobId AND (j.owner_user_id IS NULL OR j.owner_user_id=:userId)
            """)
            .param("userId", userId)
            .param("jobId", jobId)
            .query(this::card)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    jdbc.sql(
            """
            INSERT INTO user_job_states(id,user_id,job_id,first_viewed_at,last_viewed_at,created_at,updated_at)
            VALUES (:id,:userId,:jobId,:now,:now,:now,:now)
            ON CONFLICT(user_id,job_id) DO UPDATE SET last_viewed_at=EXCLUDED.last_viewed_at,
              updated_at=EXCLUDED.updated_at,version=user_job_states.version+1
            """)
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("jobId", jobId)
        .param("now", now())
        .update();
    AnalysisRow analysis =
        jdbc.sql(
                "SELECT analysis_json,validation_status,model_id,created_at FROM job_ai_analyses WHERE job_id=:jobId AND superseded_at IS NULL ORDER BY created_at DESC LIMIT 1")
            .param("jobId", jobId)
            .query(this::analysis)
            .optional()
            .orElse(null);
    String description =
        jdbc.sql("SELECT description_text FROM jobs WHERE id=:jobId")
            .param("jobId", jobId)
            .query(String.class)
            .single();
    List<String> links =
        jdbc
            .sql(
                "SELECT COALESCE(application_url,source_url) FROM job_source_occurrences WHERE job_id=:jobId ORDER BY first_seen_at")
            .param("jobId", jobId)
            .query(String.class)
            .list()
            .stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    List<String> regions =
        jdbc.sql("SELECT region_code FROM job_regions WHERE job_id=:jobId ORDER BY region_code")
            .param("jobId", jobId)
            .query(String.class)
            .list();
    return new JobDetail(
        card,
        description,
        links.isEmpty() ? null : links.getFirst(),
        links,
        regions,
        analysis == null ? null : read(analysis.json()),
        analysis == null ? null : analysis.status(),
        analysis == null ? null : analysis.model(),
        analysis == null ? null : analysis.createdAt());
  }

  @Transactional
  public JobState state(UUID userId, UUID jobId, String action) {
    requireVisible(userId, jobId);
    String column =
        switch (action) {
          case "save" -> "saved";
          case "unsave" -> "saved";
          case "hide", "restore" -> "hidden";
          case "archive" -> "archived";
          default ->
              throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported state action");
        };
    boolean value = action.equals("save") || action.equals("hide") || action.equals("archive");
    jdbc.sql(
            "INSERT INTO user_job_states(id,user_id,job_id,"
                + column
                + ",created_at,updated_at) VALUES (:id,:userId,:jobId,:value,:now,:now) ON CONFLICT(user_id,job_id) DO UPDATE SET "
                + column
                + "=:value,updated_at=:now,version=user_job_states.version+1")
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("jobId", jobId)
        .param("value", value)
        .param("now", now())
        .update();
    return jdbc.sql(
            "SELECT saved,hidden,archived FROM user_job_states WHERE user_id=:userId AND job_id=:jobId")
        .param("userId", userId)
        .param("jobId", jobId)
        .query(
            (rs, row) ->
                new JobState(
                    rs.getBoolean("saved"), rs.getBoolean("hidden"), rs.getBoolean("archived")))
        .single();
  }

  String where(Search query) {
    List<String> clauses = new ArrayList<>();
    clauses.add("(j.owner_user_id IS NULL OR j.owner_user_id=:userId)");
    clauses.add("j.source_status='ACTIVE'");
    if (!query.includeHidden()) clauses.add("COALESCE(us.hidden,false)=false");
    if (!query.includeArchived()) clauses.add("COALESCE(us.archived,false)=false");
    if (query.saved()) clauses.add("COALESCE(us.saved,false)=true");
    if (present(query.q())) clauses.add("j.search_vector @@ websearch_to_tsquery('simple',:q)");
    if (present(query.section()))
      clauses.add(
          "EXISTS(SELECT 1 FROM job_regions r WHERE r.job_id=j.id AND r.region_code=:section)");
    if (present(query.aiRelevance())) clauses.add("j.ai_relevance=:ai");
    if (present(query.annotationRelevance())) clauses.add("j.annotation_relevance=:annotation");
    if (present(query.workplaceMode())) clauses.add("j.workplace_mode=:workplace");
    if (present(query.remoteScope())) clauses.add("j.remote_scope=:remote");
    if ("PERSONAL_AI_SOFTWARE".equalsIgnoreCase(query.focus())) {
      clauses.add("lower(j.original_title) ~ :targetTitlePattern");
      clauses.add("lower(j.original_title) !~ :unrelatedTitlePattern");
    }
    if ("JUNIOR_ENTRY".equalsIgnoreCase(query.experienceLevel())) {
      clauses.add("j.seniority NOT IN ('SENIOR','STAFF','LEAD','MANAGER')");
      clauses.add("lower(j.original_title) !~ :seniorTitlePattern");
      clauses.add(
          "(j.seniority='JUNIOR' OR j.required_years_min<=2 OR lower(j.original_title) ~ '(^|[^a-z])(junior|entry[ -]?level|graduate|associate|trainee|apprentice|engineer i|developer i)([^a-z]|$)')");
    } else if ("EXCLUDE_SENIOR".equalsIgnoreCase(query.experienceLevel())) {
      clauses.add("j.seniority NOT IN ('SENIOR','STAFF','LEAD','MANAGER')");
      clauses.add("lower(j.original_title) !~ :seniorTitlePattern");
    } else if (present(query.experienceLevel())
        && !"ALL".equalsIgnoreCase(query.experienceLevel())) {
      clauses.add("j.seniority=:seniority");
    }
    return " WHERE " + String.join(" AND ", clauses);
  }

  private org.springframework.jdbc.core.simple.JdbcClient.StatementSpec params(
      org.springframework.jdbc.core.simple.JdbcClient.StatementSpec spec,
      Search query,
      UUID userId) {
    spec = spec.param("userId", userId);
    if (present(query.q())) spec = spec.param("q", query.q());
    if (present(query.section()))
      spec = spec.param("section", query.section().toUpperCase(Locale.ROOT));
    if (present(query.aiRelevance()))
      spec = spec.param("ai", query.aiRelevance().toUpperCase(Locale.ROOT));
    if (present(query.annotationRelevance()))
      spec = spec.param("annotation", query.annotationRelevance().toUpperCase(Locale.ROOT));
    if (present(query.workplaceMode()))
      spec = spec.param("workplace", query.workplaceMode().toUpperCase(Locale.ROOT));
    if (present(query.remoteScope()))
      spec = spec.param("remote", query.remoteScope().toUpperCase(Locale.ROOT));
    if ("PERSONAL_AI_SOFTWARE".equalsIgnoreCase(query.focus())) {
      spec = spec.param("targetTitlePattern", TARGET_TITLE_PATTERN);
      spec = spec.param("unrelatedTitlePattern", UNRELATED_TITLE_PATTERN);
    }
    if ("JUNIOR_ENTRY".equalsIgnoreCase(query.experienceLevel())
        || "EXCLUDE_SENIOR".equalsIgnoreCase(query.experienceLevel()))
      spec = spec.param("seniorTitlePattern", SENIOR_TITLE_PATTERN);
    else if (present(query.experienceLevel()) && !"ALL".equalsIgnoreCase(query.experienceLevel()))
      spec = spec.param("seniority", query.experienceLevel().toUpperCase(Locale.ROOT));
    return spec;
  }

  private String order(String value) {
    return switch (value == null ? "NEWEST" : value) {
      case "BEST_MATCH" ->
          """
           ORDER BY COALESCE((SELECT
             CASE m.eligibility_state WHEN 'ELIGIBLE' THEN 5000 WHEN 'LIKELY_ELIGIBLE' THEN 4000
               WHEN 'NEEDS_REVIEW' THEN 3000 WHEN 'LIKELY_INELIGIBLE' THEN 2000 ELSE 1000 END
             + m.overall_score FROM job_matches m WHERE m.user_id=:userId AND m.job_id=j.id
             ORDER BY m.created_at DESC LIMIT 1),0) DESC,j.source_posted_at DESC NULLS LAST,j.id
          """;
      case "DISCOVERED" -> " ORDER BY j.first_seen_at DESC,j.id";
      case "COMPANY" -> " ORDER BY j.company_normalized,j.canonical_title,j.id";
      case "SALARY" ->
          " ORDER BY j.salary_max DESC NULLS LAST,j.source_posted_at DESC NULLS LAST,j.id";
      default -> " ORDER BY j.source_posted_at DESC NULLS LAST,j.first_seen_at DESC,j.id";
    };
  }

  private JobCard card(ResultSet rs, int row) throws SQLException {
    return new JobCard(
        rs.getObject("id", UUID.class),
        rs.getString("original_title"),
        rs.getString("canonical_title"),
        rs.getString("company_name"),
        rs.getString("location"),
        rs.getString("source"),
        rs.getObject("source_posted_at", OffsetDateTime.class),
        rs.getObject("first_seen_at", OffsetDateTime.class),
        rs.getObject("last_verified_at", OffsetDateTime.class),
        rs.getString("employment_type"),
        rs.getString("seniority"),
        rs.getString("workplace_mode"),
        rs.getString("remote_scope"),
        rs.getString("morocco_remote_eligibility"),
        rs.getString("primary_role_family"),
        rs.getString("ai_relevance"),
        rs.getString("annotation_relevance"),
        rs.getBigDecimal("salary_min"),
        rs.getBigDecimal("salary_max"),
        rs.getString("salary_currency"),
        rs.getBoolean("saved"),
        rs.getBoolean("hidden"),
        rs.getBoolean("archived"));
  }

  private AnalysisRow analysis(ResultSet rs, int row) throws SQLException {
    return new AnalysisRow(
        rs.getString("analysis_json"),
        rs.getString("validation_status"),
        rs.getString("model_id"),
        rs.getObject("created_at", OffsetDateTime.class));
  }

  private JobAnalysis read(String value) {
    try {
      return json.readValue(value, JobAnalysis.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored analysis is invalid", exception);
    }
  }

  private void requireVisible(UUID userId, UUID jobId) {
    boolean exists =
        jdbc.sql(
                "SELECT EXISTS(SELECT 1 FROM jobs WHERE id=:jobId AND (owner_user_id IS NULL OR owner_user_id=:userId))")
            .param("jobId", jobId)
            .param("userId", userId)
            .query(Boolean.class)
            .single();
    if (!exists) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
  }

  private boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public record Search(
      String q,
      String section,
      String aiRelevance,
      String annotationRelevance,
      String workplaceMode,
      String remoteScope,
      String focus,
      String experienceLevel,
      boolean saved,
      boolean includeHidden,
      boolean includeArchived,
      String sort,
      int page,
      int size) {}

  private record AnalysisRow(String json, String status, String model, OffsetDateTime createdAt) {}
}

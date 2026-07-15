package com.aijobradar.documents.application;

import com.aijobradar.documents.application.DeterministicResumePlanner.Fact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class TailoredResumeService {
  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final Clock clock;
  private final DeterministicResumePlanner planner;
  private final ResumeFactValidator validator;
  private final ResumeDocumentRenderer renderer;
  private final ResumeVersionPolicy versionPolicy;

  public TailoredResumeService(
      JdbcClient jdbc,
      ObjectMapper json,
      Clock clock,
      DeterministicResumePlanner planner,
      ResumeFactValidator validator,
      ResumeDocumentRenderer renderer,
      ResumeVersionPolicy versionPolicy) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.planner = planner;
    this.validator = validator;
    this.renderer = renderer;
    this.versionPolicy = versionPolicy;
  }

  public Set<String> variants() {
    return DeterministicResumePlanner.VARIANTS;
  }

  @Transactional
  public TailoredResume generate(UUID userId, UUID jobId, String variant) {
    String title =
        jdbc.sql(
                "SELECT original_title FROM jobs WHERE id=:jobId AND (owner_user_id IS NULL OR owner_user_id=:userId)")
            .param("jobId", jobId)
            .param("userId", userId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    List<Fact> facts = facts(userId);
    ResumeContent content = planner.plan(variant, title, facts, missing(userId, jobId));
    validator.validate(content, factIds(facts));
    UUID resumeId =
        jdbc.sql(
                "SELECT id FROM tailored_resumes WHERE user_id=:userId AND job_id=:jobId AND variant=:variant")
            .param("userId", userId)
            .param("jobId", jobId)
            .param("variant", variant)
            .query(UUID.class)
            .optional()
            .orElse(null);
    OffsetDateTime now = now();
    int version;
    if (resumeId == null) {
      resumeId = UUID.randomUUID();
      version = 1;
      jdbc.sql(
              """
              INSERT INTO tailored_resumes(id,user_id,job_id,variant,title,current_version,created_at,updated_at)
              VALUES (:id,:userId,:jobId,:variant,:title,1,:now,:now)
              """)
          .param("id", resumeId)
          .param("userId", userId)
          .param("jobId", jobId)
          .param("variant", variant)
          .param("title", title)
          .param("now", now)
          .update();
    } else {
      version =
          jdbc.sql(
                  """
                  UPDATE tailored_resumes SET current_version=current_version+1,updated_at=:now
                  WHERE id=:id AND user_id=:userId RETURNING current_version
                  """)
              .param("id", resumeId)
              .param("userId", userId)
              .param("now", now)
              .query(Integer.class)
              .single();
    }
    insertVersion(userId, resumeId, version, content, now);
    return get(userId, resumeId);
  }

  @Transactional
  public TailoredResume revise(UUID userId, UUID resumeId, ResumeContent content) {
    Current current = current(userId, resumeId);
    versionPolicy.requireEditable(current.status());
    validator.validate(content, factIds(facts(userId)));
    int version =
        jdbc.sql(
                """
                UPDATE tailored_resumes SET current_version=current_version+1,updated_at=:now
                WHERE id=:id AND user_id=:userId RETURNING current_version
                """)
            .param("id", resumeId)
            .param("userId", userId)
            .param("now", now())
            .query(Integer.class)
            .single();
    insertVersion(userId, resumeId, version, content, now());
    return get(userId, resumeId);
  }

  @Transactional
  public TailoredResume approve(UUID userId, UUID resumeId) {
    Current current = current(userId, resumeId);
    int changed =
        jdbc.sql(
                """
                UPDATE tailored_resume_versions SET status='APPROVED',approved_at=:now
                WHERE id=:versionId AND user_id=:userId AND status='DRAFT'
                """)
            .param("versionId", current.versionId())
            .param("userId", userId)
            .param("now", now())
            .update();
    if (changed == 0 && !"APPROVED".equals(current.status()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft can be approved");
    return get(userId, resumeId);
  }

  @Transactional
  public UUID lockApprovedVersion(UUID userId, UUID resumeId) {
    Current current = current(userId, resumeId);
    int changed =
        jdbc.sql(
                """
                UPDATE tailored_resume_versions SET status='LOCKED',locked_at=:now
                WHERE id=:versionId AND user_id=:userId AND status='APPROVED'
                """)
            .param("versionId", current.versionId())
            .param("userId", userId)
            .param("now", now())
            .update();
    if (changed != 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Select an approved resume version");
    return current.versionId();
  }

  public List<TailoredResume> list(UUID userId) {
    return jdbc.sql(
            """
            SELECT r.id,r.job_id,r.variant,r.title,r.current_version,v.id version_id,v.status,
              v.content_json,v.content_sha256,v.created_at
            FROM tailored_resumes r JOIN tailored_resume_versions v
              ON v.tailored_resume_id=r.id AND v.version_number=r.current_version
            WHERE r.user_id=:userId ORDER BY r.updated_at DESC
            """)
        .param("userId", userId)
        .query(this::map)
        .list();
  }

  public TailoredResume get(UUID userId, UUID resumeId) {
    return jdbc.sql(
            """
            SELECT r.id,r.job_id,r.variant,r.title,r.current_version,v.id version_id,v.status,
              v.content_json,v.content_sha256,v.created_at
            FROM tailored_resumes r JOIN tailored_resume_versions v
              ON v.tailored_resume_id=r.id AND v.version_number=r.current_version
            WHERE r.id=:id AND r.user_id=:userId
            """)
        .param("id", resumeId)
        .param("userId", userId)
        .query(this::map)
        .optional()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tailored resume not found"));
  }

  public Download download(UUID userId, UUID resumeId, String format) {
    TailoredResume resume = get(userId, resumeId);
    return switch (format.toLowerCase(java.util.Locale.ROOT)) {
      case "pdf" ->
          new Download(
              renderer.pdf(resume.content()), "application/pdf", safeName(resume.title()) + ".pdf");
      case "docx" ->
          new Download(
              renderer.docx(resume.content()),
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              safeName(resume.title()) + ".docx");
      default -> throw new IllegalArgumentException("Format must be pdf or docx");
    };
  }

  private void insertVersion(
      UUID userId, UUID resumeId, int version, ResumeContent content, OffsetDateTime now) {
    String serialized = write(content);
    jdbc.sql(
            """
            INSERT INTO tailored_resume_versions(id,user_id,tailored_resume_id,version_number,
              content_json,content_sha256,status,prompt_version,model_id,created_at)
            VALUES (:id,:userId,:resumeId,:version,CAST(:content AS jsonb),:hash,'DRAFT',
              'resume-plan-deterministic-v1','deterministic-v1',:now)
            """)
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("resumeId", resumeId)
        .param("version", version)
        .param("content", serialized)
        .param("hash", sha256(serialized))
        .param("now", now)
        .update();
  }

  private Current current(UUID userId, UUID resumeId) {
    return jdbc.sql(
            """
            SELECT v.id,v.status FROM tailored_resumes r JOIN tailored_resume_versions v
              ON v.tailored_resume_id=r.id AND v.version_number=r.current_version
            WHERE r.id=:id AND r.user_id=:userId
            """)
        .param("id", resumeId)
        .param("userId", userId)
        .query((rs, row) -> new Current(rs.getObject("id", UUID.class), rs.getString("status")))
        .optional()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tailored resume not found"));
  }

  private List<Fact> facts(UUID userId) {
    return jdbc.sql(
            """
            SELECT id,statement,skills_json FROM candidate_facts
            WHERE user_id=:userId AND verification_status='VERIFIED' ORDER BY updated_at DESC
            """)
        .param("userId", userId)
        .query(
            (rs, row) ->
                new Fact(
                    rs.getObject("id", UUID.class),
                    rs.getString("statement"),
                    read(rs.getString("skills_json"), STRINGS)))
        .list();
  }

  private List<String> missing(UUID userId, UUID jobId) {
    return jdbc.sql(
            """
            SELECT missing_requirements_json FROM job_matches
            WHERE user_id=:userId AND job_id=:jobId ORDER BY updated_at DESC LIMIT 1
            """)
        .param("userId", userId)
        .param("jobId", jobId)
        .query(String.class)
        .optional()
        .map(value -> read(value, STRINGS))
        .orElse(List.of());
  }

  private Set<UUID> factIds(List<Fact> facts) {
    return facts.stream().map(Fact::id).collect(Collectors.toUnmodifiableSet());
  }

  private TailoredResume map(ResultSet rs, int row) throws SQLException {
    ResumeContent content = read(rs.getString("content_json"), ResumeContent.class);
    return new TailoredResume(
        rs.getObject("id", UUID.class),
        rs.getObject("job_id", UUID.class),
        rs.getString("variant"),
        rs.getString("title"),
        rs.getInt("current_version"),
        rs.getObject("version_id", UUID.class),
        rs.getString("status"),
        content,
        renderer.html(content),
        rs.getString("content_sha256"),
        rs.getObject("created_at", OffsetDateTime.class));
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Resume content cannot be serialized", exception);
    }
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored resume content is invalid", exception);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored resume value is invalid", exception);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private String safeName(String value) {
    String result = value.replaceAll("[^a-zA-Z0-9._-]+", "-");
    return result.isBlank() ? "tailored-resume" : result;
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private record Current(UUID versionId, String status) {}

  public record TailoredResume(
      UUID id,
      UUID jobId,
      String variant,
      String title,
      int version,
      UUID versionId,
      String status,
      ResumeContent content,
      String previewHtml,
      String contentSha256,
      OffsetDateTime createdAt) {}

  public record Download(byte[] content, String contentType, String filename) {}
}

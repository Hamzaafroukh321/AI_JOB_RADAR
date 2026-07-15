package com.aijobradar.applications.application;

import com.aijobradar.applications.application.ApplicationStateMachine.State;
import com.aijobradar.documents.application.TailoredResumeService;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApplicationService {
  private final JdbcClient jdbc;
  private final Clock clock;
  private final ApplicationStateMachine states;
  private final TailoredResumeService resumes;

  public ApplicationService(
      JdbcClient jdbc, Clock clock, ApplicationStateMachine states, TailoredResumeService resumes) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.states = states;
    this.resumes = resumes;
  }

  @Transactional
  public OpenResult open(UUID userId, UUID jobId) {
    String url =
        jdbc.sql(
                """
                SELECT COALESCE(
                  (SELECT application_url FROM job_source_occurrences
                   WHERE job_id=j.id AND active AND application_url IS NOT NULL
                   ORDER BY updated_at DESC LIMIT 1),
                  (SELECT source_url FROM job_source_occurrences
                   WHERE job_id=j.id AND active AND source_url IS NOT NULL
                   ORDER BY updated_at DESC LIMIT 1))
                FROM jobs j WHERE j.id=:jobId AND (j.owner_user_id IS NULL OR j.owner_user_id=:userId)
                """)
            .param("jobId", jobId)
            .param("userId", userId)
            .query(String.class)
            .optional()
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Application URL not found"));
    Application application = ensure(userId, jobId, State.OPENED);
    event(
        userId,
        application.id(),
        "EXTERNAL_APPLICATION_OPENED",
        application.state(),
        application.state(),
        null);
    return new OpenResult(application, url);
  }

  @Transactional
  public Application markApplied(UUID userId, UUID jobId, UUID resumeId, boolean confirmed) {
    if (!confirmed)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Applied confirmation is required");
    UUID versionId = resumes.lockApprovedVersion(userId, resumeId);
    Application current = find(userId, jobId).orElseGet(() -> create(userId, jobId, State.OPENED));
    states.requireTransition(current.state(), State.APPLIED);
    OffsetDateTime now = now();
    jdbc.sql(
            """
            UPDATE applications SET state='APPLIED',tailored_resume_version_id=:versionId,
              applied_at=:now,updated_at=:now,version=version+1
            WHERE id=:id AND user_id=:userId
            """)
        .param("versionId", versionId)
        .param("now", now)
        .param("id", current.id())
        .param("userId", userId)
        .update();
    event(
        userId, current.id(), "STATE_CHANGED", current.state(), State.APPLIED, "Applied manually");
    return get(userId, current.id());
  }

  @Transactional
  public Application markNotApplied(UUID userId, UUID applicationId, boolean confirmed) {
    if (!confirmed)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Removing Applied requires confirmation");
    Application current = get(userId, applicationId);
    states.requireTransition(current.state(), State.OPENED);
    jdbc.sql(
            """
            UPDATE applications SET state='OPENED',applied_at=NULL,updated_at=:now,version=version+1
            WHERE id=:id AND user_id=:userId
            """)
        .param("now", now())
        .param("id", applicationId)
        .param("userId", userId)
        .update();
    event(
        userId,
        applicationId,
        "STATE_CHANGED",
        current.state(),
        State.OPENED,
        "Applied reversed manually");
    return get(userId, applicationId);
  }

  @Transactional
  public Application transition(UUID userId, UUID applicationId, State target) {
    Application current = get(userId, applicationId);
    states.requireTransition(current.state(), target);
    jdbc.sql(
            "UPDATE applications SET state=:state,updated_at=:now,version=version+1 WHERE id=:id AND user_id=:userId")
        .param("state", target.name())
        .param("now", now())
        .param("id", applicationId)
        .param("userId", userId)
        .update();
    event(userId, applicationId, "STATE_CHANGED", current.state(), target, null);
    return get(userId, applicationId);
  }

  @Transactional
  public Event note(UUID userId, UUID applicationId, String note) {
    Application current = get(userId, applicationId);
    if (note == null || note.isBlank()) throw new IllegalArgumentException("Note is required");
    return event(
        userId, applicationId, "NOTE_ADDED", current.state(), current.state(), note.trim());
  }

  @Transactional
  public Reminder reminder(
      UUID userId, UUID applicationId, OffsetDateTime remindAt, String message) {
    get(userId, applicationId);
    if (remindAt == null || !remindAt.isAfter(now()))
      throw new IllegalArgumentException("Reminder must be in the future");
    UUID id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO application_reminders(id,user_id,application_id,remind_at,message,created_at)
            VALUES (:id,:userId,:applicationId,:remindAt,:message,:now)
            """)
        .param("id", id)
        .param("userId", userId)
        .param("applicationId", applicationId)
        .param("remindAt", remindAt)
        .param("message", message)
        .param("now", now())
        .update();
    event(userId, applicationId, "REMINDER_CREATED", null, null, message);
    return new Reminder(id, applicationId, remindAt, message, false);
  }

  public List<Application> list(UUID userId) {
    return jdbc.sql(
            """
            SELECT a.id,a.job_id,j.original_title,j.company_name,a.state,
              a.tailored_resume_version_id,a.applied_at,a.updated_at
            FROM applications a JOIN jobs j ON j.id=a.job_id
            WHERE a.user_id=:userId ORDER BY a.updated_at DESC
            """)
        .param("userId", userId)
        .query(this::map)
        .list();
  }

  public List<Event> events(UUID userId, UUID applicationId) {
    get(userId, applicationId);
    return jdbc.sql(
            """
            SELECT id,event_type,from_state,to_state,note,created_at FROM application_events
            WHERE user_id=:userId AND application_id=:applicationId ORDER BY created_at DESC
            """)
        .param("userId", userId)
        .param("applicationId", applicationId)
        .query(
            (rs, row) ->
                new Event(
                    rs.getObject("id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("from_state"),
                    rs.getString("to_state"),
                    rs.getString("note"),
                    rs.getObject("created_at", OffsetDateTime.class)))
        .list();
  }

  public byte[] exportCsv(UUID userId) {
    StringBuilder csv =
        new StringBuilder("job_id,title,company,state,resume_version_id,applied_at,updated_at\r\n");
    for (Application application : list(userId)) {
      csv.append(csv(application.jobId().toString()))
          .append(',')
          .append(csv(application.title()))
          .append(',')
          .append(csv(application.company()))
          .append(',')
          .append(application.state())
          .append(',')
          .append(
              csv(
                  application.resumeVersionId() == null
                      ? ""
                      : application.resumeVersionId().toString()))
          .append(',')
          .append(csv(application.appliedAt() == null ? "" : application.appliedAt().toString()))
          .append(',')
          .append(csv(application.updatedAt().toString()))
          .append("\r\n");
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private Application ensure(UUID userId, UUID jobId, State initial) {
    return find(userId, jobId).orElseGet(() -> create(userId, jobId, initial));
  }

  private Application create(UUID userId, UUID jobId, State initial) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = now();
    jdbc.sql(
            """
            INSERT INTO applications(id,user_id,job_id,state,created_at,updated_at)
            VALUES (:id,:userId,:jobId,:state,:now,:now)
            """)
        .param("id", id)
        .param("userId", userId)
        .param("jobId", jobId)
        .param("state", initial.name())
        .param("now", now)
        .update();
    event(userId, id, "CREATED", null, initial, null);
    return get(userId, id);
  }

  private java.util.Optional<Application> find(UUID userId, UUID jobId) {
    return jdbc.sql(
            """
            SELECT a.id,a.job_id,j.original_title,j.company_name,a.state,
              a.tailored_resume_version_id,a.applied_at,a.updated_at
            FROM applications a JOIN jobs j ON j.id=a.job_id
            WHERE a.user_id=:userId AND a.job_id=:jobId
            """)
        .param("userId", userId)
        .param("jobId", jobId)
        .query(this::map)
        .optional();
  }

  private Application get(UUID userId, UUID applicationId) {
    return jdbc.sql(
            """
            SELECT a.id,a.job_id,j.original_title,j.company_name,a.state,
              a.tailored_resume_version_id,a.applied_at,a.updated_at
            FROM applications a JOIN jobs j ON j.id=a.job_id
            WHERE a.user_id=:userId AND a.id=:id
            """)
        .param("userId", userId)
        .param("id", applicationId)
        .query(this::map)
        .optional()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
  }

  private Application map(ResultSet rs, int row) throws SQLException {
    return new Application(
        rs.getObject("id", UUID.class),
        rs.getObject("job_id", UUID.class),
        rs.getString("original_title"),
        rs.getString("company_name"),
        State.valueOf(rs.getString("state")),
        rs.getObject("tailored_resume_version_id", UUID.class),
        rs.getObject("applied_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private Event event(
      UUID userId, UUID applicationId, String type, State from, State to, String note) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = now();
    jdbc.sql(
            """
            INSERT INTO application_events(id,user_id,application_id,event_type,from_state,to_state,note,created_at)
            VALUES (:id,:userId,:applicationId,:type,:fromState,:toState,:note,:now)
            """)
        .param("id", id)
        .param("userId", userId)
        .param("applicationId", applicationId)
        .param("type", type)
        .param("fromState", from == null ? null : from.name())
        .param("toState", to == null ? null : to.name())
        .param("note", note)
        .param("now", now)
        .update();
    return new Event(
        id, type, from == null ? null : from.name(), to == null ? null : to.name(), note, now);
  }

  private String csv(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public record Application(
      UUID id,
      UUID jobId,
      String title,
      String company,
      State state,
      UUID resumeVersionId,
      OffsetDateTime appliedAt,
      OffsetDateTime updatedAt) {}

  public record OpenResult(Application application, String applicationUrl) {}

  public record Event(
      UUID id,
      String type,
      String fromState,
      String toState,
      String note,
      OffsetDateTime createdAt) {}

  public record Reminder(
      UUID id, UUID applicationId, OffsetDateTime remindAt, String message, boolean completed) {}
}

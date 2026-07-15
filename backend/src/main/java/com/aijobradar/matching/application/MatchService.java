package com.aijobradar.matching.application;

import com.aijobradar.jobs.application.JobAnalysis;
import com.aijobradar.matching.application.MatchEngine.CandidateContext;
import com.aijobradar.matching.application.MatchEngine.VerifiedFact;
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
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MatchService {
  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
  private final JdbcClient jdbc;
  private final MatchEngine engine;
  private final MatchRecomputeQueue queue;
  private final ObjectMapper json;
  private final Clock clock;

  public MatchService(
      JdbcClient jdbc,
      MatchEngine engine,
      MatchRecomputeQueue queue,
      ObjectMapper json,
      Clock clock) {
    this.jdbc = jdbc;
    this.engine = engine;
    this.queue = queue;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public MatchResult getOrCompute(UUID userId, UUID jobId, boolean force) {
    Inputs inputs = inputs(userId, jobId);
    if (!force) {
      MatchResult existing =
          jdbc.sql(
                  """
                  SELECT * FROM job_matches
                  WHERE user_id=:userId AND job_id=:jobId AND candidate_profile_version=:profileVersion
                    AND job_analysis_id=:analysisId
                  """)
              .param("userId", userId)
              .param("jobId", jobId)
              .param("profileVersion", inputs.profileVersion())
              .param("analysisId", inputs.analysisId())
              .query(this::match)
              .optional()
              .orElse(null);
      if (existing != null) return existing;
    }
    MatchResult calculated =
        engine.calculate(
            jobId, inputs.analysis(), inputs.facts(), inputs.candidate(), inputs.expiresAt());
    UUID id = UUID.randomUUID();
    OffsetDateTime now = now();
    UUID persistedId =
        jdbc.sql(
                """
            INSERT INTO job_matches(id,user_id,job_id,candidate_profile_version,job_analysis_id,
              eligibility_state,overall_score,confidence,component_scores_json,strong_matches_json,
              partial_matches_json,missing_requirements_json,unknowns_json,hard_blockers_json,
              recommended_action,one_sentence_rationale,prompt_version,model_id,created_at,updated_at)
            VALUES (:id,:userId,:jobId,:profileVersion,:analysisId,:eligibility,:score,:confidence,
              CAST(:components AS jsonb),CAST(:strong AS jsonb),CAST(:partial AS jsonb),
              CAST(:missing AS jsonb),CAST(:unknowns AS jsonb),CAST(:blockers AS jsonb),
              :action,:rationale,'deterministic-match-v1','deterministic-v1',:now,:now)
            ON CONFLICT(user_id,job_id,candidate_profile_version,job_analysis_id)
            DO UPDATE SET eligibility_state=EXCLUDED.eligibility_state,overall_score=EXCLUDED.overall_score,
              confidence=EXCLUDED.confidence,component_scores_json=EXCLUDED.component_scores_json,
              strong_matches_json=EXCLUDED.strong_matches_json,partial_matches_json=EXCLUDED.partial_matches_json,
              missing_requirements_json=EXCLUDED.missing_requirements_json,unknowns_json=EXCLUDED.unknowns_json,
              hard_blockers_json=EXCLUDED.hard_blockers_json,recommended_action=EXCLUDED.recommended_action,
              one_sentence_rationale=EXCLUDED.one_sentence_rationale,updated_at=EXCLUDED.updated_at
            RETURNING id
            """)
            .param("id", id)
            .param("userId", userId)
            .param("jobId", jobId)
            .param("profileVersion", inputs.profileVersion())
            .param("analysisId", inputs.analysisId())
            .param("eligibility", calculated.eligibilityState())
            .param("score", calculated.overallScore())
            .param("confidence", calculated.confidence())
            .param("components", write(calculated.componentScores()))
            .param("strong", write(calculated.strongMatches()))
            .param("partial", write(calculated.partialMatches()))
            .param("missing", write(calculated.missingRequirements()))
            .param("unknowns", write(calculated.unknowns()))
            .param("blockers", write(calculated.hardBlockers()))
            .param("action", calculated.recommendedAction())
            .param("rationale", calculated.rationale())
            .param("now", now)
            .query(UUID.class)
            .single();
    queue.requested(userId, jobId);
    return new MatchResult(
        persistedId,
        jobId,
        calculated.eligibilityState(),
        calculated.overallScore(),
        calculated.confidence(),
        calculated.componentScores(),
        calculated.strongMatches(),
        calculated.partialMatches(),
        calculated.missingRequirements(),
        calculated.unknowns(),
        calculated.hardBlockers(),
        calculated.userQuestions(),
        calculated.recommendedAction(),
        calculated.rationale());
  }

  @Transactional
  public void feedback(UUID userId, UUID jobId, String type, String note) {
    requireVisible(userId, jobId);
    UUID matchId =
        jdbc.sql(
                "SELECT id FROM job_matches WHERE user_id=:userId AND job_id=:jobId ORDER BY created_at DESC LIMIT 1")
            .param("userId", userId)
            .param("jobId", jobId)
            .query(UUID.class)
            .optional()
            .orElse(null);
    jdbc.sql(
            """
            INSERT INTO match_feedback(id,user_id,job_id,job_match_id,feedback_type,note,created_at)
            VALUES (:id,:userId,:jobId,:matchId,:type,:note,:now)
            """)
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("jobId", jobId)
        .param("matchId", matchId)
        .param("type", type)
        .param("note", note)
        .param("now", now())
        .update();
  }

  private Inputs inputs(UUID userId, UUID jobId) {
    requireVisible(userId, jobId);
    AnalysisRow analysis =
        jdbc.sql(
                """
                SELECT a.id,a.analysis_json,j.expires_at FROM job_ai_analyses a
                JOIN jobs j ON j.id=a.job_id
                WHERE a.job_id=:jobId AND a.superseded_at IS NULL ORDER BY a.created_at DESC LIMIT 1
                """)
            .param("jobId", jobId)
            .query(
                (rs, row) ->
                    new AnalysisRow(
                        rs.getObject("id", UUID.class),
                        read(rs.getString("analysis_json"), JobAnalysis.class),
                        rs.getObject("expires_at", OffsetDateTime.class)))
            .optional()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT, "Job analysis is unavailable"));
    List<VerifiedFact> facts =
        jdbc.sql(
                """
                SELECT id,statement,skills_json FROM candidate_facts
                WHERE user_id=:userId AND verification_status='VERIFIED'
                """)
            .param("userId", userId)
            .query(
                (rs, row) ->
                    new VerifiedFact(
                        rs.getObject("id", UUID.class),
                        rs.getString("statement"),
                        readStrings(rs.getString("skills_json"))))
            .list();
    ProfileRow profile =
        jdbc.sql(
                """
                SELECT p.profile_version,p.relocation_preference,p.sponsorship_required,
                  COALESCE(cp.workplace_modes_json,'[]') workplace_modes_json,
                  EXISTS(SELECT 1 FROM candidate_languages l WHERE l.user_id=p.user_id AND l.verified_by_user=true) languages_verified
                FROM candidate_profiles p LEFT JOIN candidate_preferences cp ON cp.user_id=p.user_id
                WHERE p.user_id=:userId
                """)
            .param("userId", userId)
            .query(this::profile)
            .optional()
            .orElse(new ProfileRow(1, false, false, false, List.of()));
    return new Inputs(
        analysis.id(),
        analysis.analysis(),
        analysis.expiresAt(),
        profile.version(),
        facts,
        new CandidateContext(
            profile.relocationAllowed(),
            profile.sponsorshipRequired(),
            profile.languagesVerified(),
            profile.workplaceModes()));
  }

  private ProfileRow profile(ResultSet rs, int row) throws SQLException {
    return new ProfileRow(
        rs.getLong("profile_version"),
        !"NO".equals(rs.getString("relocation_preference"))
            && !"REMOTE_ONLY".equals(rs.getString("relocation_preference")),
        rs.getBoolean("sponsorship_required"),
        rs.getBoolean("languages_verified"),
        readStrings(rs.getString("workplace_modes_json")));
  }

  private MatchResult match(ResultSet rs, int row) throws SQLException {
    return new MatchResult(
        rs.getObject("id", UUID.class),
        rs.getObject("job_id", UUID.class),
        rs.getString("eligibility_state"),
        rs.getInt("overall_score"),
        rs.getDouble("confidence"),
        read(rs.getString("component_scores_json"), new TypeReference<>() {}),
        read(rs.getString("strong_matches_json"), new TypeReference<>() {}),
        read(rs.getString("partial_matches_json"), new TypeReference<>() {}),
        read(rs.getString("missing_requirements_json"), STRINGS),
        read(rs.getString("unknowns_json"), STRINGS),
        read(rs.getString("hard_blockers_json"), STRINGS),
        questionsFromUnknowns(read(rs.getString("unknowns_json"), STRINGS)),
        rs.getString("recommended_action"),
        rs.getString("one_sentence_rationale"));
  }

  private List<String> questionsFromUnknowns(List<String> unknowns) {
    return unknowns.stream().map(value -> "Please confirm: " + value + "?").toList();
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

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Match value cannot be serialized", exception);
    }
  }

  private List<String> readStrings(String value) {
    return read(value, STRINGS);
  }

  private <T> T read(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored match input is invalid", exception);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored match value is invalid", exception);
    }
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private record AnalysisRow(UUID id, JobAnalysis analysis, OffsetDateTime expiresAt) {}

  private record ProfileRow(
      long version,
      boolean relocationAllowed,
      boolean sponsorshipRequired,
      boolean languagesVerified,
      List<String> workplaceModes) {}

  private record Inputs(
      UUID analysisId,
      JobAnalysis analysis,
      OffsetDateTime expiresAt,
      long profileVersion,
      List<VerifiedFact> facts,
      CandidateContext candidate) {}
}

package com.aijobradar.matching.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aijobradar.jobs.application.JobAnalysis;
import com.aijobradar.matching.application.MatchEngine.CandidateContext;
import com.aijobradar.matching.application.MatchEngine.VerifiedFact;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchEngineTest {
  private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
  private final MatchEngine engine = new MatchEngine(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void positiveMatchesAlwaysCiteVerifiedFacts() {
    UUID factId = UUID.randomUUID();
    MatchResult result =
        engine.calculate(
            UUID.randomUUID(),
            analysis("ENGINEERING", "HIGH", "NONE", "ELIGIBLE", "WORLDWIDE"),
            List.of(
                new VerifiedFact(
                    factId, "Built production Java Spring AI services", List.of("Java"))),
            candidate(),
            OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));

    assertThat(result.strongMatches()).isNotEmpty();
    assertThat(result.strongMatches())
        .allSatisfy(match -> assertThat(match.verifiedFactIds()).contains(factId));
  }

  @Test
  void annotationRolesUseTheAnnotationScoreProfile() {
    MatchResult result =
        engine.calculate(
            UUID.randomUUID(),
            analysis("AI_TRAINING_DATA", "MEDIUM", "HIGH", "ELIGIBLE", "WORLDWIDE"),
            List.of(
                new VerifiedFact(
                    UUID.randomUUID(),
                    "Performed LLM evaluation, annotation, and quality review",
                    List.of("evaluation"))),
            candidate(),
            null);

    assertThat(result.componentScores())
        .containsKeys("aiTrainingEvidence", "taskTypeMatch", "qualityReviewEvidence")
        .doesNotContainKey("requiredSkillCoverage");
  }

  @Test
  void hardEligibilityAndExpiryCapsOverrideSimilarity() {
    JobAnalysis excluded =
        analysis("ENGINEERING", "HIGH", "NONE", "INELIGIBLE", "COUNTRY_RESTRICTED");
    List<VerifiedFact> facts =
        List.of(
            new VerifiedFact(
                UUID.randomUUID(),
                "Senior Java Spring AI engineer with production systems",
                List.of("Java")));

    MatchResult blocked = engine.calculate(UUID.randomUUID(), excluded, facts, candidate(), null);
    MatchResult expired =
        engine.calculate(
            UUID.randomUUID(),
            analysis("ENGINEERING", "HIGH", "NONE", "ELIGIBLE", "WORLDWIDE"),
            facts,
            candidate(),
            OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));

    assertThat(blocked.eligibilityState()).isEqualTo("LIKELY_INELIGIBLE");
    assertThat(blocked.overallScore()).isLessThanOrEqualTo(45);
    assertThat(expired.eligibilityState()).isEqualTo("INELIGIBLE");
    assertThat(expired.overallScore()).isZero();
  }

  @Test
  void ambiguousRemoteScopeRequiresReviewInsteadOfAssumingWorldwide() {
    MatchResult result =
        engine.calculate(
            UUID.randomUUID(),
            analysis("ENGINEERING", "HIGH", "NONE", "UNKNOWN", "UNKNOWN"),
            List.of(),
            candidate(),
            null);

    assertThat(result.eligibilityState()).isEqualTo("NEEDS_REVIEW");
    assertThat(result.overallScore()).isLessThanOrEqualTo(79);
    assertThat(result.userQuestions()).contains("Does the employer allow working from Morocco?");
  }

  private CandidateContext candidate() {
    return new CandidateContext(false, false, true, List.of("REMOTE"));
  }

  private JobAnalysis analysis(
      String role, String ai, String annotation, String morocco, String remoteScope) {
    return new JobAnalysis(
        "Build reliable AI services",
        role,
        List.of(),
        ai,
        annotation,
        "MID",
        List.of("FULL_TIME"),
        "REMOTE",
        remoteScope,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        morocco,
        List.of(),
        List.of(new JobAnalysis.Requirement("Java", "SKILL", "Java required", 0.95)),
        List.of(),
        List.of("Java"),
        List.of(),
        null,
        null,
        "UNKNOWN",
        List.of(),
        List.of(),
        List.of(),
        0.9);
  }
}

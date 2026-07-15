package com.aijobradar.matching.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MatchResult(
    UUID id,
    UUID jobId,
    String eligibilityState,
    int overallScore,
    double confidence,
    Map<String, Integer> componentScores,
    List<EvidenceMatch> strongMatches,
    List<EvidenceMatch> partialMatches,
    List<String> missingRequirements,
    List<String> unknowns,
    List<String> hardBlockers,
    List<String> userQuestions,
    String recommendedAction,
    String rationale) {
  public record EvidenceMatch(String label, String jobEvidence, List<UUID> verifiedFactIds) {}
}

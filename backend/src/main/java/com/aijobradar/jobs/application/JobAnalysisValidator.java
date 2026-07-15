package com.aijobradar.jobs.application;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JobAnalysisValidator {
  private static final Set<String> RELEVANCE = Set.of("HIGH", "MEDIUM", "LOW", "NONE");
  private static final Set<String> WORKPLACE = Set.of("ONSITE", "HYBRID", "REMOTE", "UNKNOWN");
  private static final Set<String> REMOTE =
      Set.of(
          "WORLDWIDE",
          "COUNTRY_LIST",
          "REGION_LIST",
          "TIMEZONE_RESTRICTED",
          "COUNTRY_AND_TIMEZONE_RESTRICTED",
          "UNKNOWN");
  private static final Set<String> MOROCCO =
      Set.of("ELIGIBLE", "POSSIBLY_ELIGIBLE", "INELIGIBLE", "UNKNOWN");

  public List<String> validate(JobAnalysis analysis) {
    java.util.ArrayList<String> errors = new java.util.ArrayList<>();
    if (analysis == null) return List.of("analysis is required");
    required(analysis.jobSummary(), "jobSummary", errors);
    required(analysis.primaryRoleFamily(), "primaryRoleFamily", errors);
    enumValue(analysis.aiRelevance(), RELEVANCE, "aiRelevance", errors);
    enumValue(analysis.annotationRelevance(), RELEVANCE, "annotationRelevance", errors);
    enumValue(analysis.workplaceMode(), WORKPLACE, "workplaceMode", errors);
    enumValue(analysis.remoteScope(), REMOTE, "remoteScope", errors);
    enumValue(analysis.moroccoEligibility(), MOROCCO, "moroccoEligibility", errors);
    if (analysis.overallConfidence() < 0 || analysis.overallConfidence() > 1)
      errors.add("overallConfidence must be between 0 and 1");
    if ("WORLDWIDE".equals(analysis.remoteScope()) && !"REMOTE".equals(analysis.workplaceMode()))
      errors.add("WORLDWIDE requires REMOTE workplace mode");
    if (analysis.mustHaveRequirements() != null)
      analysis
          .mustHaveRequirements()
          .forEach(
              item -> {
                required(item.requirement(), "requirement", errors);
                required(item.evidence(), "requirement evidence", errors);
                if (item.confidence() < 0 || item.confidence() > 1)
                  errors.add("requirement confidence is invalid");
              });
    return List.copyOf(errors);
  }

  private void enumValue(String value, Set<String> allowed, String name, List<String> errors) {
    if (!allowed.contains(value)) errors.add(name + " is invalid");
  }

  private void required(String value, String name, List<String> errors) {
    if (value == null || value.isBlank()) errors.add(name + " is required");
  }
}

package com.aijobradar.jobs.application;

import java.util.List;

public record JobAnalysis(
    String jobSummary,
    String primaryRoleFamily,
    List<String> secondaryRoleFamilies,
    String aiRelevance,
    String annotationRelevance,
    String seniority,
    List<String> employmentTypes,
    String workplaceMode,
    String remoteScope,
    List<String> allowedCountries,
    List<String> excludedCountries,
    List<String> allowedRegions,
    List<String> timezoneRequirements,
    String moroccoEligibility,
    List<EvidenceItem> responsibilities,
    List<Requirement> mustHaveRequirements,
    List<Requirement> niceToHaveRequirements,
    List<String> technologies,
    List<LanguageRequirement> requiredLanguages,
    Double requiredYearsMin,
    Double requiredYearsMax,
    String visaSponsorship,
    List<String> citizenshipRequirements,
    List<String> securityClearanceRequirements,
    List<String> warnings,
    double overallConfidence) {

  public record EvidenceItem(String text, String evidence) {}

  public record Requirement(String requirement, String type, String evidence, double confidence) {}

  public record LanguageRequirement(String language, String level, String evidence) {}
}

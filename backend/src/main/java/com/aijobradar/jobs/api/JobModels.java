package com.aijobradar.jobs.api;

import com.aijobradar.jobs.application.JobAnalysis;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class JobModels {
  private JobModels() {}

  public record JobCard(
      UUID id,
      String originalTitle,
      String canonicalTitle,
      String company,
      String location,
      String source,
      OffsetDateTime sourcePostedAt,
      OffsetDateTime firstSeenAt,
      OffsetDateTime lastVerifiedAt,
      String employmentType,
      String seniority,
      String workplaceMode,
      String remoteScope,
      String moroccoEligibility,
      String primaryRoleFamily,
      String aiRelevance,
      String annotationRelevance,
      BigDecimal salaryMin,
      BigDecimal salaryMax,
      String salaryCurrency,
      boolean saved,
      boolean hidden,
      boolean archived) {}

  public record JobPage(
      List<JobCard> content, int page, int size, long totalElements, int totalPages) {}

  public record JobDetail(
      JobCard job,
      String description,
      String applicationUrl,
      List<String> sourceLinks,
      List<String> regions,
      JobAnalysis analysis,
      String analysisValidationStatus,
      String analysisModel,
      OffsetDateTime analysisCreatedAt) {}

  public record JobState(boolean saved, boolean hidden, boolean archived) {}
}

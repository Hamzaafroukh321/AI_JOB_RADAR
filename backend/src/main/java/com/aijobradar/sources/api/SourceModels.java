package com.aijobradar.sources.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class SourceModels {
  private SourceModels() {}

  public record SourceInput(
      @NotBlank @Size(max = 120) String key,
      @NotBlank @Size(max = 200) String displayName,
      @NotBlank String type,
      @Size(max = 1000) String baseUrl,
      @Size(max = 1000) String termsUrl,
      @NotBlank String termsReviewStatus,
      boolean credentialsRequired,
      @Size(max = 120) String scheduleCron,
      @NotBlank String timezone,
      int priority,
      @NotBlank String parserVersion,
      @NotNull Map<String, String> configuration) {}

  public record SourceView(
      UUID id,
      String key,
      String displayName,
      String type,
      String baseUrl,
      String termsUrl,
      String termsReviewStatus,
      boolean credentialsRequired,
      boolean enabled,
      String scheduleCron,
      String timezone,
      int priority,
      String parserVersion,
      Map<String, String> configuration,
      int consecutiveFailures,
      OffsetDateTime lastAttemptedAt,
      OffsetDateTime lastSuccessfulAt,
      String lastRunStatus,
      long totalJobs) {}

  public record FetchRunView(
      UUID id,
      UUID sourceId,
      String triggerType,
      String status,
      OffsetDateTime startedAt,
      OffsetDateTime finishedAt,
      int httpCalls,
      int received,
      int inserted,
      int updated,
      int deduplicated,
      int ignored,
      String errorCategory,
      String safeError) {}

  public record ManualImportInput(
      @NotBlank @Size(max = 500) String title,
      @NotBlank @Size(max = 300) String company,
      @Size(max = 500) String location,
      @Size(max = 2000) String sourceUrl,
      @Size(max = 2000) String applicationUrl,
      @NotBlank @Size(max = 100_000) String description,
      @Size(max = 80) String employmentType,
      @Size(max = 80) String workplaceMode) {}

  public record ManualImportResult(UUID fetchRunId, UUID jobId, boolean created) {}
}

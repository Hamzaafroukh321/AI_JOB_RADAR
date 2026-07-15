package com.aijobradar.profile.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProfileModels {
  private ProfileModels() {}

  public record Profile(
      UUID id,
      String headline,
      String homeCountryCode,
      String homeRegion,
      String currentCity,
      String relocationPreference,
      Boolean sponsorshipRequired,
      UUID activeMasterResumeId,
      long profileVersion) {}

  public record ProfileUpdate(
      @Size(max = 240) String headline,
      @Pattern(regexp = "[A-Za-z]{2}") String homeCountryCode,
      @Size(max = 160) String homeRegion,
      @Size(max = 160) String currentCity,
      @Pattern(regexp = "UNKNOWN|NO|DOMESTIC|INTERNATIONAL|REMOTE_ONLY") String relocationPreference,
      Boolean sponsorshipRequired) {}

  public record MinimumSalary(BigDecimal amount, String currency) {}

  public record WorkingHours(String timezone, List<String> preferredOverlap) {}

  public record Preferences(
      List<String> targetRoleFamilies,
      List<String> targetSeniority,
      List<String> preferredRegions,
      List<String> preferredCountries,
      List<String> excludedCountries,
      List<String> employmentTypes,
      List<String> workplaceModes,
      MinimumSalary minimumSalary,
      Boolean contractAllowed,
      Boolean freelanceAllowed,
      Boolean annotationWorkAllowed,
      Boolean temporaryWorkAllowed,
      boolean dailyDigestEnabled,
      LocalTime dailyDigestTime,
      @Min(0) @Max(100) int minimumMatchScore,
      @Min(1) @Max(365) int freshnessDays,
      List<String> excludedCompanies,
      List<String> excludedKeywords,
      List<String> includedKeywords,
      WorkingHours workingHours,
      List<String> preferredCompanySizes,
      List<String> preferredIndustries) {}

  public record Language(
      UUID id,
      String languageCode,
      String spokenLevel,
      String writtenLevel,
      Boolean professionalUse,
      boolean verifiedByUser) {}

  public record LanguageInput(
      @NotBlank @Size(max = 16) String languageCode,
      @Pattern(regexp = "UNKNOWN|BASIC|CONVERSATIONAL|PROFESSIONAL|FLUENT|NATIVE") String spokenLevel,
      @Pattern(regexp = "UNKNOWN|BASIC|CONVERSATIONAL|PROFESSIONAL|FLUENT|NATIVE") String writtenLevel,
      Boolean professionalUse) {}

  public record Authorization(
      UUID id,
      String countryCode,
      String authorizationStatus,
      Boolean sponsorshipNeeded,
      LocalDate expiresAt,
      boolean verifiedByUser,
      String notes) {}

  public record AuthorizationInput(
      @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
      @Pattern(regexp = "UNKNOWN|AUTHORIZED|NOT_AUTHORIZED|CITIZEN|PERMANENT_RESIDENT") String authorizationStatus,
      Boolean sponsorshipNeeded,
      LocalDate expiresAt,
      @Size(max = 1000) String notes) {}

  public record MasterResume(
      UUID id,
      String name,
      String languageCode,
      String originalFilename,
      String mimeType,
      long sizeBytes,
      String sha256,
      String extractionStatus,
      boolean active,
      OffsetDateTime createdAt,
      String extractionPreview,
      List<TextBlock> blocks) {}

  public record ResumeLanguageInput(@NotBlank @Size(max = 16) String languageCode) {}

  public record TextBlock(
      Integer page, int paragraph, String section, String text, int startOffset, int endOffset) {}

  public record CandidateFact(
      UUID id,
      UUID masterResumeId,
      String factType,
      String organization,
      String roleTitle,
      String statement,
      LocalDate startDate,
      LocalDate endDate,
      List<String> skills,
      Integer sourcePage,
      Integer sourceStartOffset,
      Integer sourceEndOffset,
      String verificationStatus,
      boolean userEdited,
      UUID supersedesFactId,
      OffsetDateTime createdAt) {}

  public record CandidateFactInput(
      UUID masterResumeId,
      @NotBlank @Size(max = 48) String factType,
      @Size(max = 200) String organization,
      @Size(max = 200) String roleTitle,
      @NotBlank @Size(max = 8000) String statement,
      LocalDate startDate,
      LocalDate endDate,
      List<@Size(max = 120) String> skills,
      Integer sourcePage,
      Integer sourceStartOffset,
      Integer sourceEndOffset) {}
}

package com.aijobradar.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("radar")
public record RadarProperties(
    @NotBlank String environment,
    List<String> allowedOrigins,
    @Valid Seed seed,
    @Valid Storage storage,
    @Valid Ai ai) {
  public record Seed(
      boolean enabled,
      String email,
      String password,
      String displayName,
      boolean customizeProfile) {}

  public record Storage(
      boolean enabled,
      @NotBlank String endpoint,
      @NotBlank String region,
      @NotBlank String bucket,
      String accessKey,
      String secretKey) {}

  public record Ai(
      boolean enabled,
      String groqApiKey,
      String baseUrl,
      String fastModel,
      String qualityModel,
      int requestTimeoutSeconds,
      int maxRetries,
      int dailyTokenBudget) {}
}

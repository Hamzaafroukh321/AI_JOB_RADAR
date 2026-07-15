package com.aijobradar.ai.application;

import java.util.List;

public interface LanguageModelProvider {
  boolean isEnabled();

  <T> AiResult<T> generateStructured(
      AiTaskType taskType,
      List<AiMessage> messages,
      String jsonSchema,
      Class<T> responseType,
      AiRequestOptions options);

  enum AiTaskType {
    JOB_ANALYSIS,
    REQUIREMENT_EXTRACTION,
    REMOTE_SCOPE
  }

  record AiMessage(String role, String content) {}

  record AiRequestOptions(String modelRoute, int maxTokens, double temperature) {}

  record AiResult<T>(
      boolean success,
      T value,
      String modelId,
      Integer requestTokens,
      Integer responseTokens,
      long latencyMs,
      int retries,
      String errorCategory,
      String safeError) {
    public static <T> AiResult<T> disabled() {
      return new AiResult<>(
          false, null, "disabled", null, null, 0, 0, "DISABLED", "AI provider is disabled");
    }
  }
}

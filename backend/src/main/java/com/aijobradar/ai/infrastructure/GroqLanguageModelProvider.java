package com.aijobradar.ai.infrastructure;

import com.aijobradar.ai.application.LanguageModelProvider;
import com.aijobradar.common.config.RadarProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "radar.ai.enabled", havingValue = "true")
public final class GroqLanguageModelProvider implements LanguageModelProvider {
  private final RadarProperties.Ai config;
  private final ObjectMapper json;
  private final HttpClient http;

  public GroqLanguageModelProvider(RadarProperties properties, ObjectMapper json) {
    this.config = properties.ai();
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    requireConfiguration();
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public <T> AiResult<T> generateStructured(
      AiTaskType taskType,
      List<AiMessage> messages,
      String jsonSchema,
      Class<T> responseType,
      AiRequestOptions options) {
    long started = System.nanoTime();
    String model =
        "QUALITY".equals(options.modelRoute()) ? config.qualityModel() : config.fastModel();
    try {
      HttpResponse<String> response = send(taskType, messages, jsonSchema, options, model, true);
      int retries = 0;
      if (response.statusCode() == 400 && config.maxRetries() > 0) {
        response = send(taskType, messages, jsonSchema, options, model, false);
        retries = 1;
      }
      if (response.statusCode() == 429)
        return failure(model, started, "RATE_LIMITED", "AI provider rate limit reached");
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        return failure(model, started, "PROVIDER_ERROR", "AI provider rejected the request");
      JsonNode root = json.readTree(response.body());
      String content = root.path("choices").path(0).path("message").path("content").asText();
      T value = json.readValue(content, responseType);
      JsonNode usage = root.path("usage");
      return new AiResult<>(
          true,
          value,
          model,
          integer(usage, "prompt_tokens"),
          integer(usage, "completion_tokens"),
          elapsed(started),
          retries,
          null,
          null);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return failure(model, started, "INTERRUPTED", "AI request was interrupted");
    } catch (Exception exception) {
      return failure(model, started, "PROVIDER_ERROR", "AI provider request failed");
    }
  }

  private HttpResponse<String> send(
      AiTaskType taskType,
      List<AiMessage> messages,
      String jsonSchema,
      AiRequestOptions options,
      String model,
      boolean strict)
      throws Exception {
    Map<String, Object> responseFormat;
    if (strict) {
      Map<String, Object> schema =
          Map.of(
              "name",
              taskType.name().toLowerCase(java.util.Locale.ROOT),
              "strict",
              true,
              "schema",
              json.readTree(jsonSchema));
      responseFormat = Map.of("type", "json_schema", "json_schema", schema);
    } else {
      responseFormat = Map.of("type", "json_object");
    }
    Map<String, Object> request =
        Map.of(
            "model", model,
            "messages", messages,
            "temperature", options.temperature(),
            "max_completion_tokens", options.maxTokens(),
            "response_format", responseFormat);
    HttpRequest httpRequest =
        HttpRequest.newBuilder(endpoint())
            .timeout(Duration.ofSeconds(Math.max(1, config.requestTimeoutSeconds())))
            .header("Authorization", "Bearer " + config.groqApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request)))
            .build();
    return http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private <T> AiResult<T> failure(String model, long started, String category, String detail) {
    return new AiResult<>(false, null, model, null, null, elapsed(started), 0, category, detail);
  }

  private Integer integer(JsonNode node, String field) {
    return node.path(field).isNumber() ? node.path(field).asInt() : null;
  }

  private long elapsed(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }

  private URI endpoint() {
    return URI.create(config.baseUrl().replaceAll("/+$", "") + "/chat/completions");
  }

  private void requireConfiguration() {
    URI base = URI.create(config.baseUrl());
    if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null)
      throw new IllegalStateException("Groq base URL must be HTTPS");
    if (config.groqApiKey() == null || config.groqApiKey().isBlank())
      throw new IllegalStateException("AI is enabled but GROQ_API_KEY is unavailable");
    if (config.fastModel().isBlank() || config.qualityModel().isBlank())
      throw new IllegalStateException("Configured Groq models are required");
  }
}

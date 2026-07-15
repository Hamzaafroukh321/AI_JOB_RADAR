package com.aijobradar.sources.infrastructure.connectors;

import com.aijobradar.sources.application.JobSourceConnector;
import com.aijobradar.sources.application.UnsafeSourceException;
import com.aijobradar.sources.infrastructure.ConnectorHttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdzunaConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public AdzunaConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "ADZUNA";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(true, false, true, false, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Adzuna search endpoint is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String appId = credential(source, "appIdEnv", "ADZUNA_APP_ID");
    String appKey = credential(source, "appKeyEnv", "ADZUNA_APP_KEY");
    String country =
        source.configuration().getOrDefault("countryCode", "gb").toLowerCase(java.util.Locale.ROOT);
    if (!country.matches("[a-z]{2}"))
      throw new UnsafeSourceException("CONFIGURATION", "Adzuna country code is invalid");
    int page = context.cursor() == null ? 1 : parsePage(context.cursor());
    String base = source.baseUrl() == null ? "https://api.adzuna.com" : source.baseUrl();
    String query = source.configuration().getOrDefault("query", "artificial intelligence");
    String endpoint =
        base
            + "/v1/api/jobs/"
            + country
            + "/search/"
            + page
            + "?app_id="
            + encode(appId)
            + "&app_key="
            + encode(appKey)
            + "&results_per_page=50&what="
            + encode(query);
    List<RawJobRecord> records = parse(http.get(endpoint, Map.of()).body());
    return FetchResult.success(
        records, records.size() == 50 ? Integer.toString(page + 1) : null, 1);
  }

  public List<RawJobRecord> parse(String body) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode results = root.path("results");
      if (!results.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Adzuna results array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : results) {
        String id = ConnectorParsing.text(job, "id");
        String title = ConnectorParsing.text(job, "title");
        if (id == null || title == null) continue;
        records.add(
            new RawJobRecord(
                id,
                ConnectorParsing.text(job, "redirect_url"),
                ConnectorParsing.text(job, "redirect_url"),
                title,
                ConnectorParsing.text(job.path("company"), "display_name"),
                ConnectorParsing.text(job.path("location"), "display_name"),
                ConnectorParsing.text(job, "description"),
                job.toString(),
                ConnectorParsing.instant(ConnectorParsing.text(job, "created")),
                null,
                ConnectorParsing.text(job, "contract_time"),
                null,
                null));
      }
      return records;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Adzuna response is invalid");
    }
  }

  private String credential(
      SourceConfiguration source, String referenceKey, String defaultEnvironmentName) {
    String environmentName =
        source.configuration().getOrDefault(referenceKey, defaultEnvironmentName);
    if (!environmentName.matches("[A-Z][A-Z0-9_]{2,80}"))
      throw new UnsafeSourceException("CONFIGURATION", "Adzuna credential reference is required");
    String value = System.getenv(environmentName);
    if (value == null || value.isBlank())
      throw new UnsafeSourceException(
          "CREDENTIALS_UNAVAILABLE", "Adzuna credentials are unavailable");
    return value;
  }

  private int parsePage(String value) {
    try {
      return Math.max(1, Integer.parseInt(value));
    } catch (NumberFormatException exception) {
      throw new UnsafeSourceException("CURSOR", "Adzuna cursor is invalid");
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

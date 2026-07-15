package com.aijobradar.sources.infrastructure.connectors;

import com.aijobradar.sources.application.JobSourceConnector;
import com.aijobradar.sources.application.UnsafeSourceException;
import com.aijobradar.sources.infrastructure.ConnectorHttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RemotiveConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public RemotiveConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "REMOTIVE";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(false, false, true, true, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Remotive public API is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String base = source.baseUrl() == null ? "https://remotive.com" : source.baseUrl();
    String category =
        source.configuration().getOrDefault("category", "artificial-intelligence").trim();
    if (!category.matches("[a-z0-9-]{2,60}"))
      throw new UnsafeSourceException("CONFIGURATION", "Remotive category is invalid");
    int limit = limit(source.configuration().getOrDefault("limit", "100"));
    String endpoint =
        base
            + "/api/remote-jobs?category="
            + URLEncoder.encode(category, StandardCharsets.UTF_8)
            + "&limit="
            + limit;
    List<RawJobRecord> records = parse(http.get(endpoint, Map.of()).body());
    return FetchResult.success(records, null, 1);
  }

  public List<RawJobRecord> parse(String body) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode jobs = root.path("jobs");
      if (!jobs.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Remotive jobs array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : jobs) {
        String id = ConnectorParsing.text(job, "id");
        String title = ConnectorParsing.text(job, "title");
        String url = ConnectorParsing.text(job, "url");
        if (id == null || title == null || url == null) continue;
        records.add(
            new RawJobRecord(
                id,
                url,
                url,
                title,
                ConnectorParsing.text(job, "company_name"),
                ConnectorParsing.text(job, "candidate_required_location"),
                ConnectorParsing.text(job, "description"),
                job.toString(),
                published(ConnectorParsing.text(job, "publication_date")),
                null,
                ConnectorParsing.text(job, "job_type"),
                "REMOTE",
                null));
      }
      return records;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Remotive response is invalid");
    }
  }

  private Instant published(String value) {
    Instant parsed = ConnectorParsing.instant(value);
    if (parsed != null || value == null || value.isBlank()) return parsed;
    try {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private int limit(String value) {
    try {
      return Math.max(1, Math.min(100, Integer.parseInt(value)));
    } catch (NumberFormatException exception) {
      throw new UnsafeSourceException("CONFIGURATION", "Remotive limit is invalid");
    }
  }
}

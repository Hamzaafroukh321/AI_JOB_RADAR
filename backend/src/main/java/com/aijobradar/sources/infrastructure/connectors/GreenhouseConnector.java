package com.aijobradar.sources.infrastructure.connectors;

import com.aijobradar.sources.application.JobSourceConnector;
import com.aijobradar.sources.application.UnsafeSourceException;
import com.aijobradar.sources.infrastructure.ConnectorHttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GreenhouseConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public GreenhouseConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "GREENHOUSE";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(false, false, false, true, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Public job board is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String token = required(source, "boardToken");
    String base = source.baseUrl() == null ? "https://boards-api.greenhouse.io" : source.baseUrl();
    String body =
        http.get(base + "/v1/boards/" + segment(token) + "/jobs?content=true", Map.of()).body();
    return FetchResult.success(parse(body), null, 1);
  }

  public List<RawJobRecord> parse(String body) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode jobs = root.path("jobs");
      if (!jobs.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Greenhouse jobs array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : jobs) {
        String id = ConnectorParsing.text(job, "id");
        String title = ConnectorParsing.text(job, "title");
        if (id == null || title == null) continue;
        JsonNode location = job.path("location");
        records.add(
            new RawJobRecord(
                id,
                ConnectorParsing.text(job, "absolute_url"),
                ConnectorParsing.text(job, "absolute_url"),
                title,
                null,
                ConnectorParsing.text(location, "name"),
                ConnectorParsing.text(job, "content"),
                job.toString(),
                null,
                ConnectorParsing.instant(ConnectorParsing.text(job, "updated_at")),
                null,
                null,
                null));
      }
      return records;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Greenhouse response is invalid");
    }
  }

  private String required(SourceConfiguration source, String key) {
    String value = source.configuration().get(key);
    if (value == null || value.isBlank())
      throw new UnsafeSourceException("CONFIGURATION", "Greenhouse board token is required");
    return value;
  }

  private String segment(String value) {
    if (!value.matches("[A-Za-z0-9_-]+"))
      throw new UnsafeSourceException("CONFIGURATION", "Greenhouse board token is invalid");
    return value;
  }
}

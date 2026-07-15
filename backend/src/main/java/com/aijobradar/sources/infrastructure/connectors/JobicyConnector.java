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
public class JobicyConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public JobicyConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "JOBICY";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(false, false, false, true, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Jobicy public API is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String base = source.baseUrl() == null ? "https://jobicy.com" : source.baseUrl();
    String tag = source.configuration().getOrDefault("tag", "artificial intelligence").trim();
    String geo = source.configuration().getOrDefault("geo", "").trim().toLowerCase();
    if (tag.length() < 3 || tag.length() > 50)
      throw new UnsafeSourceException(
          "CONFIGURATION", "Jobicy tag must contain between 3 and 50 characters");
    if (!geo.isEmpty() && !geo.matches("[a-z0-9-]{2,40}"))
      throw new UnsafeSourceException("CONFIGURATION", "Jobicy geography is invalid");
    int count = count(source.configuration().getOrDefault("count", "100"));
    StringBuilder endpoint =
        new StringBuilder(base)
            .append("/api/v2/remote-jobs?count=")
            .append(count)
            .append("&tag=")
            .append(encode(tag));
    if (!geo.isEmpty()) endpoint.append("&geo=").append(encode(geo));
    List<RawJobRecord> records = parse(http.get(endpoint.toString(), Map.of()).body());
    return FetchResult.success(records, null, 1);
  }

  public List<RawJobRecord> parse(String body) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode jobs = root.path("jobs");
      if (!jobs.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Jobicy jobs array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : jobs) {
        String id = ConnectorParsing.text(job, "id");
        String title = ConnectorParsing.text(job, "jobTitle");
        String url = ConnectorParsing.text(job, "url");
        if (id == null || title == null || url == null) continue;
        records.add(
            new RawJobRecord(
                id,
                url,
                url,
                title,
                ConnectorParsing.text(job, "companyName"),
                ConnectorParsing.text(job, "jobGeo"),
                ConnectorParsing.text(job, "jobDescription"),
                job.toString(),
                ConnectorParsing.instant(ConnectorParsing.text(job, "pubDate")),
                null,
                firstText(job, "jobType"),
                "REMOTE",
                null));
      }
      return records;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Jobicy response is invalid");
    }
  }

  private int count(String value) {
    try {
      return Math.max(1, Math.min(100, Integer.parseInt(value)));
    } catch (NumberFormatException exception) {
      throw new UnsafeSourceException("CONFIGURATION", "Jobicy count is invalid");
    }
  }

  private String firstText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isArray()) return value.isEmpty() ? null : value.get(0).asText();
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

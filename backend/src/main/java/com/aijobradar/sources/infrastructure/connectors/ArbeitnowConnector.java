package com.aijobradar.sources.infrastructure.connectors;

import com.aijobradar.sources.application.JobSourceConnector;
import com.aijobradar.sources.application.UnsafeSourceException;
import com.aijobradar.sources.infrastructure.ConnectorHttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ArbeitnowConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public ArbeitnowConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "ARBEITNOW";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(true, false, false, true, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Arbeitnow public API is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String base = source.baseUrl() == null ? "https://www.arbeitnow.com" : source.baseUrl();
    int configuredPages = pages(source.configuration().getOrDefault("pages", "5"));
    int pageCount = Math.min(configuredPages, Math.max(1, context.pageLimit()));
    List<RawJobRecord> records = new ArrayList<>();
    for (int page = 1; page <= pageCount; page++) {
      String body = http.get(base + "/api/job-board-api?page=" + page, Map.of()).body();
      Page parsed = parsePage(body);
      records.addAll(parsed.records());
      if (!parsed.hasNext()) return FetchResult.success(records, null, page);
    }
    return FetchResult.success(records, null, pageCount);
  }

  public List<RawJobRecord> parse(String body) {
    return parsePage(body).records();
  }

  private Page parsePage(String body) {
    try {
      JsonNode root = json.readTree(body);
      JsonNode jobs = root.path("data");
      if (!jobs.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Arbeitnow data array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : jobs) {
        String id = ConnectorParsing.text(job, "slug");
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
                ConnectorParsing.text(job, "location"),
                ConnectorParsing.text(job, "description"),
                job.toString(),
                epoch(job.path("created_at")),
                null,
                firstText(job.path("job_types")),
                job.path("remote").asBoolean(false) ? "REMOTE" : "UNKNOWN",
                null));
      }
      JsonNode next = root.path("links").path("next");
      return new Page(records, !next.isMissingNode() && !next.isNull() && !next.asText().isBlank());
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Arbeitnow response is invalid");
    }
  }

  private Instant epoch(JsonNode value) {
    return value.canConvertToLong() ? Instant.ofEpochSecond(value.asLong()) : null;
  }

  private String firstText(JsonNode values) {
    return values.isArray() && !values.isEmpty() ? values.get(0).asText() : null;
  }

  private int pages(String value) {
    try {
      return Math.max(1, Math.min(5, Integer.parseInt(value)));
    } catch (NumberFormatException exception) {
      throw new UnsafeSourceException("CONFIGURATION", "Arbeitnow page count is invalid");
    }
  }

  private record Page(List<RawJobRecord> records, boolean hasNext) {}
}

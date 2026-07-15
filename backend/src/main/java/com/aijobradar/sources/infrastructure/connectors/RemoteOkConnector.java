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
public class RemoteOkConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public RemoteOkConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "REMOTEOK";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(false, false, true, true, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Remote OK public JSON feed is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String base = source.baseUrl() == null ? "https://remoteok.com" : source.baseUrl();
    return FetchResult.success(parse(http.get(base + "/api", Map.of()).body()), null, 1);
  }

  public List<RawJobRecord> parse(String body) {
    try {
      JsonNode root = json.readTree(body);
      if (!root.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Remote OK response array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : root) {
        String id = ConnectorParsing.text(job, "id");
        String title = ConnectorParsing.text(job, "position");
        String url = ConnectorParsing.text(job, "url");
        if (id == null || title == null || url == null) continue;
        String applyUrl = ConnectorParsing.text(job, "apply_url");
        records.add(
            new RawJobRecord(
                id,
                url,
                applyUrl == null ? url : applyUrl,
                title,
                ConnectorParsing.text(job, "company"),
                ConnectorParsing.text(job, "location"),
                ConnectorParsing.text(job, "description"),
                job.toString(),
                epoch(job.path("epoch")),
                null,
                null,
                "REMOTE",
                null));
      }
      return records;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Remote OK response is invalid");
    }
  }

  private Instant epoch(JsonNode value) {
    return value.canConvertToLong() ? Instant.ofEpochSecond(value.asLong()) : null;
  }
}

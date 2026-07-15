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
public class LeverConnector implements JobSourceConnector {
  private final ConnectorHttpClient http;
  private final ObjectMapper json;

  public LeverConnector(ConnectorHttpClient http, ObjectMapper json) {
    this.http = http;
    this.json = json;
  }

  @Override
  public String type() {
    return "LEVER";
  }

  @Override
  public ConnectorCapabilities capabilities() {
    return new ConnectorCapabilities(true, false, false, true, false);
  }

  @Override
  public ConnectorHealth healthCheck(SourceConfiguration source) {
    try {
      fetch(new FetchContext(java.util.UUID.randomUUID(), null, null, 1), source);
      return new ConnectorHealth(true, "OK", "Public postings endpoint is reachable");
    } catch (UnsafeSourceException exception) {
      return new ConnectorHealth(false, exception.category(), exception.getMessage());
    }
  }

  @Override
  public FetchResult fetch(FetchContext context, SourceConfiguration source) {
    String site = source.configuration().get("site");
    if (site == null || !site.matches("[A-Za-z0-9_-]+"))
      throw new UnsafeSourceException("CONFIGURATION", "Lever site is required");
    int limit = limit(source.configuration().get("limit"), context.pageLimit());
    int skip = cursor(context.cursor());
    String host =
        "EU".equalsIgnoreCase(source.configuration().get("region"))
            ? "https://api.eu.lever.co"
            : "https://api.lever.co";
    if (source.baseUrl() != null) host = source.baseUrl();
    String endpoint = host + "/v0/postings/" + site + "?mode=json&limit=" + limit + "&skip=" + skip;
    List<RawJobRecord> records = parse(http.get(endpoint, Map.of()).body());
    String next = records.size() == limit ? Integer.toString(skip + records.size()) : null;
    return FetchResult.success(records, next, 1);
  }

  public List<RawJobRecord> parse(String body) {
    try {
      JsonNode root = json.readTree(body);
      if (!root.isArray())
        throw new UnsafeSourceException("SCHEMA_CHANGED", "Lever postings array is missing");
      List<RawJobRecord> records = new ArrayList<>();
      for (JsonNode job : root) {
        String id = ConnectorParsing.text(job, "id");
        String title = ConnectorParsing.text(job, "text");
        if (id == null || title == null) continue;
        JsonNode categories = job.path("categories");
        String description = ConnectorParsing.text(job, "descriptionPlain");
        if (description == null) description = ConnectorParsing.text(job, "description");
        records.add(
            new RawJobRecord(
                id,
                ConnectorParsing.text(job, "hostedUrl"),
                ConnectorParsing.text(job, "applyUrl"),
                title,
                null,
                ConnectorParsing.text(categories, "location"),
                description,
                job.toString(),
                null,
                null,
                ConnectorParsing.text(categories, "commitment"),
                ConnectorParsing.text(categories, "workplaceType"),
                null));
      }
      return records;
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new UnsafeSourceException("INVALID_JSON", "Lever response is invalid");
    }
  }

  private int cursor(String value) {
    if (value == null || value.isBlank()) return 0;
    try {
      return Math.max(0, Integer.parseInt(value));
    } catch (NumberFormatException exception) {
      throw new UnsafeSourceException("CURSOR", "Lever cursor is invalid");
    }
  }

  int limit(String configured, int pageLimit) {
    int maximum = Math.min(Math.max(pageLimit, 1) * 100, 500);
    if (configured == null || configured.isBlank()) return maximum;
    try {
      return Math.min(Math.max(1, Integer.parseInt(configured)), maximum);
    } catch (NumberFormatException exception) {
      throw new UnsafeSourceException("CONFIGURATION", "Lever result limit is invalid");
    }
  }
}

package com.aijobradar.sources.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface JobSourceConnector {
  String type();

  ConnectorCapabilities capabilities();

  ConnectorHealth healthCheck(SourceConfiguration source);

  FetchResult fetch(FetchContext context, SourceConfiguration source);

  record ConnectorCapabilities(
      boolean pagination,
      boolean cursor,
      boolean salary,
      boolean workplaceMode,
      boolean expiration) {}

  record ConnectorHealth(boolean healthy, String category, String detail) {}

  record FetchContext(UUID fetchRunId, Instant since, String cursor, int pageLimit) {}

  record SourceConfiguration(
      UUID id,
      String key,
      String displayName,
      String type,
      String baseUrl,
      String parserVersion,
      Map<String, String> configuration) {}

  record RawJobRecord(
      String externalId,
      String sourceUrl,
      String applicationUrl,
      String rawTitle,
      String rawCompany,
      String rawLocation,
      String rawDescription,
      String rawPayload,
      Instant sourcePostedAt,
      Instant sourceUpdatedAt,
      String employmentType,
      String workplaceMode,
      String nextCursorHint) {}

  record FetchResult(
      List<RawJobRecord> records,
      String nextCursor,
      int httpCalls,
      boolean complete,
      String errorCategory,
      String safeError) {
    public static FetchResult success(List<RawJobRecord> records, String cursor, int calls) {
      return new FetchResult(records, cursor, calls, true, null, null);
    }
  }
}

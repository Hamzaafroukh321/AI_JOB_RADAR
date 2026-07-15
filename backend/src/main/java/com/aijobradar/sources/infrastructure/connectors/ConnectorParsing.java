package com.aijobradar.sources.infrastructure.connectors;

import java.time.Instant;
import java.time.OffsetDateTime;

final class ConnectorParsing {
  private ConnectorParsing() {}

  static Instant instant(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value);
    } catch (RuntimeException ignored) {
      try {
        return OffsetDateTime.parse(value).toInstant();
      } catch (RuntimeException invalid) {
        return null;
      }
    }
  }

  static String text(tools.jackson.databind.JsonNode node, String field) {
    tools.jackson.databind.JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() ? null : value.asText();
  }
}

package com.aijobradar.sources.infrastructure.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aijobradar.sources.application.UnsafeSourceException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConnectorFixtureTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void greenhouseParsesNormalAndEmptyFixturesAndRejectsSchemaDrift() {
    GreenhouseConnector connector = new GreenhouseConnector(null, json);
    assertThat(connector.parse(fixture("greenhouse-normal.json")))
        .singleElement()
        .satisfies(job -> assertThat(job.externalId()).isEqualTo("101"));
    assertThat(connector.parse(fixture("greenhouse-empty.json"))).isEmpty();
    assertThatThrownBy(() -> connector.parse(fixture("greenhouse-schema-invalid.json")))
        .isInstanceOf(UnsafeSourceException.class)
        .hasMessageContaining("array");
  }

  @Test
  void leverParsesNormalAndEmptyFixturesAndRejectsSchemaDrift() {
    LeverConnector connector = new LeverConnector(null, json);
    assertThat(connector.parse(fixture("lever-normal.json")))
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.rawCompany()).isNull();
              assertThat(job.workplaceMode()).isEqualTo("remote");
            });
    assertThat(connector.parse(fixture("lever-empty.json"))).isEmpty();
    assertThatThrownBy(() -> connector.parse(fixture("lever-schema-invalid.json")))
        .isInstanceOf(UnsafeSourceException.class);
  }

  @Test
  void adzunaParsesNormalAndEmptyFixturesAndRejectsSchemaDrift() {
    AdzunaConnector connector = new AdzunaConnector(null, json);
    assertThat(connector.parse(fixture("adzuna-normal.json")))
        .singleElement()
        .satisfies(job -> assertThat(job.rawCompany()).isEqualTo("Acme Ltd"));
    assertThat(connector.parse(fixture("adzuna-empty.json"))).isEmpty();
    assertThatThrownBy(() -> connector.parse(fixture("adzuna-schema-invalid.json")))
        .isInstanceOf(UnsafeSourceException.class);
  }

  private String fixture(String name) {
    try (var input = getClass().getResourceAsStream("/connectors/" + name)) {
      if (input == null) throw new IllegalArgumentException("Fixture not found: " + name);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}

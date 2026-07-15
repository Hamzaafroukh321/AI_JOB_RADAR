package com.aijobradar.sources.infrastructure.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aijobradar.sources.application.JobSourceConnector.FetchContext;
import com.aijobradar.sources.application.JobSourceConnector.SourceConfiguration;
import com.aijobradar.sources.application.UnsafeSourceException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
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

  @Test
  void jobicyParsesNormalAndEmptyFixturesAndRejectsSchemaDrift() {
    JobicyConnector connector = new JobicyConnector(null, json);
    assertThat(connector.parse(fixture("jobicy-normal.json")))
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.rawCompany()).isEqualTo("Example Labs");
              assertThat(job.rawLocation()).isEqualTo("Anywhere");
              assertThat(job.workplaceMode()).isEqualTo("REMOTE");
            });
    assertThat(connector.parse(fixture("jobicy-empty.json"))).isEmpty();
    assertThatThrownBy(() -> connector.parse(fixture("jobicy-schema-invalid.json")))
        .isInstanceOf(UnsafeSourceException.class)
        .hasMessageContaining("array");
  }

  @Test
  void jobicyRejectsTagsOutsideThePublicApiLengthContract() {
    JobicyConnector connector = new JobicyConnector(null, json);
    SourceConfiguration source =
        new SourceConfiguration(
            UUID.randomUUID(),
            "jobicy-test",
            "Jobicy test",
            "JOBICY",
            "https://jobicy.com",
            "1.0.0",
            Map.of("tag", "ai"));

    assertThatThrownBy(
            () -> connector.fetch(new FetchContext(UUID.randomUUID(), null, null, 1), source))
        .isInstanceOf(UnsafeSourceException.class)
        .hasMessageContaining("between 3 and 50");
  }

  @Test
  void remotiveParsesNormalAndEmptyFixturesAndRejectsSchemaDrift() {
    RemotiveConnector connector = new RemotiveConnector(null, json);
    assertThat(connector.parse(fixture("remotive-normal.json")))
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.externalId()).isEqualTo("123");
              assertThat(job.rawTitle()).isEqualTo("Junior AI Engineer");
              assertThat(job.rawLocation()).isEqualTo("Worldwide");
              assertThat(job.workplaceMode()).isEqualTo("REMOTE");
              assertThat(job.sourcePostedAt()).isNotNull();
            });
    assertThat(connector.parse(fixture("remotive-empty.json"))).isEmpty();
    assertThatThrownBy(() -> connector.parse(fixture("remotive-schema-invalid.json")))
        .isInstanceOf(UnsafeSourceException.class)
        .hasMessageContaining("array");
  }

  @Test
  void arbeitnowParsesNormalAndEmptyFixturesAndRejectsSchemaDrift() {
    ArbeitnowConnector connector = new ArbeitnowConnector(null, json);
    assertThat(connector.parse(fixture("arbeitnow-normal.json")))
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.externalId()).isEqualTo("junior-ai-engineer-123");
              assertThat(job.rawTitle()).isEqualTo("Junior AI Engineer");
              assertThat(job.rawLocation()).isEqualTo("Berlin");
              assertThat(job.workplaceMode()).isEqualTo("REMOTE");
              assertThat(job.sourcePostedAt()).isNotNull();
            });
    assertThat(connector.parse(fixture("arbeitnow-empty.json"))).isEmpty();
    assertThatThrownBy(() -> connector.parse(fixture("arbeitnow-schema-invalid.json")))
        .isInstanceOf(UnsafeSourceException.class)
        .hasMessageContaining("array");
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

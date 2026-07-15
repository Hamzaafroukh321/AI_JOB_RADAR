package com.aijobradar.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadarPropertiesValidationTest {
  @Test
  void rejectsBlankRequiredStorageConfiguration() {
    RadarProperties properties =
        new RadarProperties(
            "development",
            List.of("http://localhost:4200"),
            new RadarProperties.Seed(false, "", "", "Local", false),
            new RadarProperties.Storage(true, "", "", "", "", ""),
            new RadarProperties.Ai(
                false, "", "https://api.groq.com/openai/v1", "fast", "quality", 60, 2, 0));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(properties))
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("storage.endpoint", "storage.region", "storage.bucket");
    }
  }
}

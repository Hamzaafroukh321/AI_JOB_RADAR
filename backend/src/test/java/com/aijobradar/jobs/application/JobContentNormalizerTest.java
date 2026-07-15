package com.aijobradar.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aijobradar.sources.application.JobSourceConnector.RawJobRecord;
import org.junit.jupiter.api.Test;

class JobContentNormalizerTest {
  private final JobContentNormalizer normalizer = new JobContentNormalizer();

  @Test
  void sanitizesHtmlNormalizesIdentityAndDropsTrackingParameters() {
    var result = normalizer.normalize(raw("<p>Build ML systems</p><script>alert(1)</script>"));
    assertThat(result.canonicalTitle()).isEqualTo("Machine Learning Engineer");
    assertThat(result.companyNormalized()).isEqualTo("acme");
    assertThat(result.descriptionHtml()).doesNotContain("script");
    assertThat(result.sourceUrl()).isEqualTo("https://example.com/jobs/1");
    assertThat(result.fingerprint()).hasSize(64);
  }

  @Test
  void sameContentProducesStableFingerprint() {
    assertThat(normalizer.normalize(raw("Build ML systems")).fingerprint())
        .isEqualTo(normalizer.normalize(raw("Build ML systems")).fingerprint());
  }

  private RawJobRecord raw(String description) {
    return new RawJobRecord(
        "1",
        "https://example.com/jobs/1?utm_source=test",
        null,
        "ML Engineer",
        "Acme Ltd",
        "Remote",
        description,
        "{}",
        null,
        null,
        "Full time",
        "Remote",
        null);
  }
}

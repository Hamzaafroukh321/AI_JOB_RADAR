package com.aijobradar.sources.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aijobradar.sources.application.UnsafeSourceException;
import org.junit.jupiter.api.Test;

class SafeUrlPolicyTest {
  private final SafeUrlPolicy policy = new SafeUrlPolicy();

  @Test
  void rejectsLocalMetadataAndCredentialedUrlsWithoutNetworkCalls() {
    assertThatThrownBy(() -> policy.requirePublicHttpUrl("http://127.0.0.1/admin"))
        .isInstanceOf(UnsafeSourceException.class);
    assertThatThrownBy(() -> policy.metadataUrl("https://user:password@example.com/jobs"))
        .isInstanceOf(UnsafeSourceException.class);
    assertThatThrownBy(() -> policy.metadataUrl("file:///etc/passwd"))
        .isInstanceOf(UnsafeSourceException.class);
  }

  @Test
  void acceptsPublicMetadataShape() {
    assertThat(policy.metadataUrl("https://api.lever.co/v0/postings/acme").getHost())
        .isEqualTo("api.lever.co");
  }
}

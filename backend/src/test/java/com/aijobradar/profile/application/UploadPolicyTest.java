package com.aijobradar.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UploadPolicyTest {
  private final UploadPolicy policy = new UploadPolicy();

  @Test
  void sanitizesFilenameAndRequiresMatchingSignatureExtensionAndMime() {
    byte[] pdf = "%PDF-1.7 synthetic".getBytes(StandardCharsets.US_ASCII);
    var upload = policy.validate("../../My résumé.pdf", "application/pdf", pdf);
    assertThat(upload.filename()).isEqualTo("My r_sum_.pdf");
    assertThat(upload.mimeType()).isEqualTo("application/pdf");

    assertThatThrownBy(() -> policy.validate("resume.docx", "application/pdf", pdf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PDF and DOCX");
  }
}

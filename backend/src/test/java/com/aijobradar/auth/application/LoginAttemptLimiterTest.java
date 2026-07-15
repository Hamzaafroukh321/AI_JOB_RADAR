package com.aijobradar.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {
  @Test
  void blocksAfterFiveFailuresAndClearsAfterSuccess() {
    LoginAttemptLimiter limiter =
        new LoginAttemptLimiter(Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC));
    for (int i = 0; i < 5; i++) limiter.failed("client:account");
    assertThat(limiter.isBlocked("client:account")).isTrue();
    limiter.succeeded("client:account");
    assertThat(limiter.isBlocked("client:account")).isFalse();
  }
}

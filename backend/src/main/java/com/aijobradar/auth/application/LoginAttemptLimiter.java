package com.aijobradar.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
  private final Clock clock;

  public LoginAttemptLimiter(Clock clock) {
    this.clock = clock;
  }

  public boolean isBlocked(String key) {
    Attempt attempt = attempts.get(key);
    if (attempt == null || attempt.first().plus(WINDOW).isBefore(clock.instant())) {
      attempts.remove(key);
      return false;
    }
    return attempt.count() >= MAX_ATTEMPTS;
  }

  public void failed(String key) {
    Instant now = clock.instant();
    attempts.compute(
        key,
        (ignored, old) ->
            old == null || old.first().plus(WINDOW).isBefore(now)
                ? new Attempt(1, now)
                : new Attempt(old.count() + 1, old.first()));
  }

  public void succeeded(String key) {
    attempts.remove(key);
  }

  private record Attempt(int count, Instant first) {}
}

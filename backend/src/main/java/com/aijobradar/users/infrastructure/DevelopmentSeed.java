package com.aijobradar.users.infrastructure;

import com.aijobradar.common.config.RadarProperties;
import com.aijobradar.users.domain.UserAccount;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Order(0)
public class DevelopmentSeed implements ApplicationRunner {
  private final RadarProperties properties;
  private final UserAccountRepository users;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public DevelopmentSeed(
      RadarProperties properties,
      UserAccountRepository users,
      PasswordEncoder encoder,
      Clock clock) {
    this.properties = properties;
    this.users = users;
    this.encoder = encoder;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.seed().enabled()) return;
    if (!"development".equals(properties.environment()))
      throw new IllegalStateException("Development seed must be disabled outside development");
    if (!StringUtils.hasText(properties.seed().email())
        || !StringUtils.hasText(properties.seed().password()))
      throw new IllegalStateException(
          "Development seed email and password are required when enabled");
    String email = properties.seed().email().toLowerCase(Locale.ROOT);
    users
        .findByEmail(email)
        .orElseGet(
            () ->
                users.save(
                    new UserAccount(
                        UUID.randomUUID(),
                        email,
                        encoder.encode(properties.seed().password()),
                        properties.seed().displayName(),
                        clock.instant())));
  }
}

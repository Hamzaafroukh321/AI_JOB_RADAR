package com.aijobradar.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.aijobradar.common.config.RadarProperties;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevelopmentSeedTest {
  @Test
  void rejectsEnabledSeedOutsideDevelopment() {
    RadarProperties properties =
        new RadarProperties(
            "production",
            List.of("https://radar.example"),
            new RadarProperties.Seed(true, "test@example.test", "synthetic", "Test", false),
            new RadarProperties.Storage(
                false, "http://localhost:9000", "us-east-1", "test", "", ""),
            new RadarProperties.Ai(
                false, "", "https://api.groq.com/openai/v1", "fast", "quality", 60, 2, 0));
    DevelopmentSeed seed =
        new DevelopmentSeed(
            properties,
            mock(UserAccountRepository.class),
            mock(PasswordEncoder.class),
            Clock.systemUTC());
    assertThatThrownBy(() -> seed.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("outside development");
  }
}

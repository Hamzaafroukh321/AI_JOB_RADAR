package com.aijobradar.dashboard.api;

import com.aijobradar.auth.application.AuthenticatedUser;
import com.aijobradar.auth.application.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final CurrentUserService currentUsers;
  private final Clock clock;

  public DashboardController(CurrentUserService currentUsers, Clock clock) {
    this.currentUsers = currentUsers;
    this.clock = clock;
  }

  @GetMapping("/summary")
  public DashboardSummary summary(Authentication authentication) {
    AuthenticatedUser user = currentUsers.require(authentication);
    return new DashboardSummary("PHASE_0", "Foundation ready", user, clock.instant());
  }

  public record DashboardSummary(
      String phase, String status, AuthenticatedUser user, Instant generatedAt) {}
}

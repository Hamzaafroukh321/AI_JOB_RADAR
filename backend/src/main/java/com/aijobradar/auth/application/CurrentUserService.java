package com.aijobradar.auth.application;

import com.aijobradar.users.domain.UserAccount;
import com.aijobradar.users.infrastructure.UserAccountRepository;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
  private final UserAccountRepository users;
  private final Clock clock;

  public CurrentUserService(UserAccountRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AuthenticatedUser require(Authentication authentication) {
    UserAccount user =
        users
            .findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Authenticated account no longer exists"));
    return new AuthenticatedUser(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone(), user.getLocale());
  }

  @Transactional
  public AuthenticatedUser recordLogin(Authentication authentication) {
    UserAccount user =
        users
            .findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Authenticated account no longer exists"));
    user.recordLogin(clock.instant());
    return new AuthenticatedUser(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone(), user.getLocale());
  }
}

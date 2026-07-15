package com.aijobradar.auth.application;

import com.aijobradar.users.domain.UserAccount;
import com.aijobradar.users.infrastructure.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
  private final UserAccountRepository users;

  public DatabaseUserDetailsService(UserAccountRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    UserAccount account =
        users
            .findByEmail(username.toLowerCase())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    return User.withUsername(account.getEmail())
        .password(account.getPasswordHash())
        .disabled(!account.isEnabled())
        .roles("USER")
        .build();
  }
}

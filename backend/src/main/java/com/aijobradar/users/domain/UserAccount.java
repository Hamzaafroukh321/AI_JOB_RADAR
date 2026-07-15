package com.aijobradar.users.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserAccount {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(nullable = false, length = 16)
  private String locale;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Version private long version;

  protected UserAccount() {}

  public UserAccount(UUID id, String email, String passwordHash, String displayName, Instant now) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.timezone = "Africa/Casablanca";
    this.locale = "en";
    this.enabled = true;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getLocale() {
    return locale;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void replaceDefaultDisplayName(String replacement, Instant now) {
    if (displayName == null || displayName.isBlank() || "Local Developer".equals(displayName)) {
      this.displayName = replacement;
      this.updatedAt = now;
    }
  }

  public void recordLogin(Instant now) {
    this.lastLoginAt = now;
    this.updatedAt = now;
  }
}

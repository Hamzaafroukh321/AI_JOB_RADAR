package com.aijobradar.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aijobradar.common.config.RadarProperties;
import com.aijobradar.profile.api.ProfileModels.Preferences;
import com.aijobradar.profile.api.ProfileModels.Profile;
import com.aijobradar.profile.api.ProfileModels.ProfileUpdate;
import com.aijobradar.profile.application.ProfileService;
import com.aijobradar.users.domain.UserAccount;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

class DevelopmentCandidatePersonalizationTest {
  @Test
  void fillsResumeBackedTargetsWithoutInventingUnknownCandidateFacts() {
    UUID userId = UUID.randomUUID();
    RadarProperties properties =
        new RadarProperties(
            "development",
            List.of("http://localhost:4200"),
            new RadarProperties.Seed(
                true, "developer@example.test", "synthetic", "Hamza Afroukh", true),
            new RadarProperties.Storage(
                false, "http://localhost:9000", "us-east-1", "test", "", ""),
            new RadarProperties.Ai(
                false, "", "https://api.groq.com/openai/v1", "fast", "quality", 60, 2, 0));
    UserAccountRepository users = mock(UserAccountRepository.class);
    ProfileService profiles = mock(ProfileService.class);
    UserAccount user =
        new UserAccount(userId, "developer@example.test", "hash", "Hamza Afroukh", Instant.EPOCH);
    when(users.findByEmail("developer@example.test")).thenReturn(Optional.of(user));
    when(profiles.getProfile(userId))
        .thenReturn(new Profile(userId, null, null, null, null, null, null, null, 0));
    when(profiles.getPreferences(userId)).thenReturn(emptyPreferences());

    new DevelopmentCandidatePersonalization(
            properties,
            users,
            profiles,
            java.time.Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC))
        .run(new DefaultApplicationArguments());

    assertThat(user.getDisplayName()).isEqualTo("Hamza Afroukh");

    ArgumentCaptor<ProfileUpdate> profile = ArgumentCaptor.forClass(ProfileUpdate.class);
    verify(profiles).updateProfile(org.mockito.ArgumentMatchers.eq(userId), profile.capture());
    assertThat(profile.getValue().headline())
        .isEqualTo(
            "Software Engineer | Java and Angular | AI/ML | AI Training and Prompt Engineering");
    assertThat(profile.getValue().homeCountryCode()).isEqualTo("MA");
    assertThat(profile.getValue().homeRegion()).isEqualTo("Rabat-Sale-Kenitra");
    assertThat(profile.getValue().currentCity()).isNull();
    assertThat(profile.getValue().relocationPreference()).isNull();
    assertThat(profile.getValue().sponsorshipRequired()).isNull();

    ArgumentCaptor<Preferences> preferences = ArgumentCaptor.forClass(Preferences.class);
    verify(profiles)
        .updatePreferences(org.mockito.ArgumentMatchers.eq(userId), preferences.capture());
    assertThat(preferences.getValue().preferredRegions())
        .containsExactly("EUROPE", "MIDDLE_EAST", "WORLDWIDE_REMOTE");
    assertThat(preferences.getValue().targetSeniority()).containsExactly("JUNIOR", "ASSOCIATE");
    assertThat(preferences.getValue().targetRoleFamilies())
        .contains("Junior generative AI engineer", "Java / Spring Boot backend developer");
    assertThat(preferences.getValue().includedKeywords())
        .contains("RAG", "prompt engineering", "Java", "Angular", "Python");
    assertThat(preferences.getValue().minimumSalary()).isNull();
    assertThat(preferences.getValue().contractAllowed()).isNull();
    assertThat(preferences.getValue().freelanceAllowed()).isNull();
    assertThat(preferences.getValue().workingHours()).isNull();
  }

  private Preferences emptyPreferences() {
    return new Preferences(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false,
        LocalTime.of(8, 0),
        50,
        30,
        List.of(),
        List.of(),
        List.of(),
        null,
        List.of(),
        List.of());
  }
}

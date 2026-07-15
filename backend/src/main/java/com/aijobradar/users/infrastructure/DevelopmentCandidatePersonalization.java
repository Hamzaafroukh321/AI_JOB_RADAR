package com.aijobradar.users.infrastructure;

import com.aijobradar.common.config.RadarProperties;
import com.aijobradar.profile.api.ProfileModels.Preferences;
import com.aijobradar.profile.api.ProfileModels.ProfileUpdate;
import com.aijobradar.profile.application.ProfileService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class DevelopmentCandidatePersonalization implements ApplicationRunner {
  static final String DISPLAY_NAME = "Hamza Afroukh";
  static final String HEADLINE =
      "Software Engineer | Java and Angular | AI/ML | AI Training and Prompt Engineering";
  static final List<String> ROLE_PRIORITIES =
      List.of(
          "AI trainer / coding evaluator / prompt evaluator",
          "Junior generative AI engineer",
          "Java AI engineer / Spring AI developer",
          "Full-stack AI developer",
          "RAG developer / LLM application engineer",
          "Junior machine-learning / applied AI engineer",
          "Technical data annotator",
          "Java / Spring Boot backend developer",
          "Angular frontend / full-stack developer");
  static final List<String> TARGET_REGIONS = List.of("EUROPE", "MIDDLE_EAST", "WORLDWIDE_REMOTE");
  static final List<String> RESUME_KEYWORDS =
      List.of(
          "AI",
          "LLM",
          "RAG",
          "prompt engineering",
          "Java",
          "Spring Boot",
          "Spring AI",
          "Angular",
          "LangChain",
          "Python",
          "machine learning",
          "full stack");

  private final RadarProperties properties;
  private final UserAccountRepository users;
  private final ProfileService profiles;
  private final Clock clock;

  public DevelopmentCandidatePersonalization(
      RadarProperties properties,
      UserAccountRepository users,
      ProfileService profiles,
      Clock clock) {
    this.properties = properties;
    this.users = users;
    this.profiles = profiles;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.seed().enabled() || !properties.seed().customizeProfile()) return;
    if (!"development".equalsIgnoreCase(properties.environment()))
      throw new IllegalStateException(
          "Candidate personalization must be disabled outside development");
    var user =
        users
            .findByEmail(properties.seed().email().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new IllegalStateException("Development seed user is unavailable"));
    user.replaceDefaultDisplayName(DISPLAY_NAME, clock.instant());
    var profile = profiles.getProfile(user.getId());
    var preferences = profiles.getPreferences(user.getId());
    boolean profileMissing =
        profile.headline() == null
            || profile.headline().isBlank()
            || profile.homeCountryCode() == null
            || profile.homeRegion() == null;
    boolean targetsMissing =
        preferences.targetRoleFamilies().isEmpty()
            || preferences.preferredRegions().isEmpty()
            || preferences.includedKeywords().isEmpty();
    if (!profileMissing && !targetsMissing) return;

    if (profileMissing) {
      profiles.updateProfile(
          user.getId(),
          new ProfileUpdate(
              profile.headline() == null || profile.headline().isBlank()
                  ? HEADLINE
                  : profile.headline(),
              profile.homeCountryCode() == null ? "MA" : profile.homeCountryCode(),
              profile.homeRegion() == null ? "Rabat-Sale-Kenitra" : profile.homeRegion(),
              profile.currentCity(),
              profile.relocationPreference(),
              profile.sponsorshipRequired()));
    }
    if (targetsMissing) {
      profiles.updatePreferences(
          user.getId(),
          new Preferences(
              preferences.targetRoleFamilies().isEmpty()
                  ? ROLE_PRIORITIES
                  : preferences.targetRoleFamilies(),
              preferences.targetSeniority().isEmpty()
                  ? List.of("JUNIOR", "ASSOCIATE")
                  : preferences.targetSeniority(),
              preferences.preferredRegions().isEmpty()
                  ? TARGET_REGIONS
                  : preferences.preferredRegions(),
              preferences.preferredCountries(),
              preferences.excludedCountries(),
              preferences.employmentTypes(),
              preferences.workplaceModes(),
              preferences.minimumSalary(),
              preferences.contractAllowed(),
              preferences.freelanceAllowed(),
              preferences.annotationWorkAllowed(),
              preferences.temporaryWorkAllowed(),
              preferences.dailyDigestEnabled(),
              preferences.dailyDigestTime(),
              preferences.minimumMatchScore(),
              preferences.freshnessDays(),
              preferences.excludedCompanies(),
              preferences.excludedKeywords(),
              preferences.includedKeywords().isEmpty()
                  ? RESUME_KEYWORDS
                  : preferences.includedKeywords(),
              preferences.workingHours(),
              preferences.preferredCompanySizes(),
              preferences.preferredIndustries()));
    }
  }
}

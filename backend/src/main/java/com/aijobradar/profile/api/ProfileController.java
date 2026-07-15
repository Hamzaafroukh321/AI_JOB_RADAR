package com.aijobradar.profile.api;

import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.profile.api.ProfileModels.Authorization;
import com.aijobradar.profile.api.ProfileModels.AuthorizationInput;
import com.aijobradar.profile.api.ProfileModels.Language;
import com.aijobradar.profile.api.ProfileModels.LanguageInput;
import com.aijobradar.profile.api.ProfileModels.Preferences;
import com.aijobradar.profile.api.ProfileModels.Profile;
import com.aijobradar.profile.api.ProfileModels.ProfileUpdate;
import com.aijobradar.profile.application.ProfileService;
import com.aijobradar.profile.application.ResumeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
  private final CurrentUserService currentUser;
  private final ProfileService profiles;
  private final ResumeService resumes;

  public ProfileController(
      CurrentUserService currentUser, ProfileService profiles, ResumeService resumes) {
    this.currentUser = currentUser;
    this.profiles = profiles;
    this.resumes = resumes;
  }

  @GetMapping
  Profile profile(Authentication authentication) {
    return profiles.getProfile(currentUser.require(authentication).id());
  }

  @PutMapping
  Profile updateProfile(Authentication authentication, @Valid @RequestBody ProfileUpdate input) {
    return profiles.updateProfile(currentUser.require(authentication).id(), input);
  }

  @GetMapping("/preferences")
  Preferences preferences(Authentication authentication) {
    return profiles.getPreferences(currentUser.require(authentication).id());
  }

  @PutMapping("/preferences")
  Preferences updatePreferences(
      Authentication authentication, @Valid @RequestBody Preferences input) {
    return profiles.updatePreferences(currentUser.require(authentication).id(), input);
  }

  @GetMapping("/languages")
  List<Language> languages(Authentication authentication) {
    return profiles.languages(currentUser.require(authentication).id());
  }

  @PostMapping("/languages")
  @ResponseStatus(HttpStatus.CREATED)
  Language createLanguage(Authentication authentication, @Valid @RequestBody LanguageInput input) {
    return profiles.saveLanguage(currentUser.require(authentication).id(), null, input);
  }

  @PutMapping("/languages/{id}")
  Language updateLanguage(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody LanguageInput input) {
    return profiles.saveLanguage(currentUser.require(authentication).id(), id, input);
  }

  @DeleteMapping("/languages/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteLanguage(Authentication authentication, @PathVariable UUID id) {
    profiles.deleteLanguage(currentUser.require(authentication).id(), id);
  }

  @GetMapping("/authorizations")
  List<Authorization> authorizations(Authentication authentication) {
    return profiles.authorizations(currentUser.require(authentication).id());
  }

  @PostMapping("/authorizations")
  @ResponseStatus(HttpStatus.CREATED)
  Authorization createAuthorization(
      Authentication authentication, @Valid @RequestBody AuthorizationInput input) {
    return profiles.saveAuthorization(currentUser.require(authentication).id(), null, input);
  }

  @PutMapping("/authorizations/{id}")
  Authorization updateAuthorization(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody AuthorizationInput input) {
    return profiles.saveAuthorization(currentUser.require(authentication).id(), id, input);
  }

  @DeleteMapping("/authorizations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteAuthorization(Authentication authentication, @PathVariable UUID id) {
    profiles.deleteAuthorization(currentUser.require(authentication).id(), id);
  }

  @PostMapping("/import-seed")
  Map<String, Integer> importSeed(Authentication authentication) {
    return Map.of(
        "imported", resumes.importCandidateSeed(currentUser.require(authentication).id()));
  }
}

package com.aijobradar.profile.api;

import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.profile.api.ProfileModels.CandidateFact;
import com.aijobradar.profile.api.ProfileModels.CandidateFactInput;
import com.aijobradar.profile.api.ProfileModels.MasterResume;
import com.aijobradar.profile.api.ProfileModels.ResumeLanguageInput;
import com.aijobradar.profile.application.ResumeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {
  private final CurrentUserService currentUser;
  private final ResumeService resumes;

  public ResumeController(CurrentUserService currentUser, ResumeService resumes) {
    this.currentUser = currentUser;
    this.resumes = resumes;
  }

  @PostMapping(path = "/master-resumes", consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  MasterResume upload(
      Authentication authentication,
      @RequestPart("file") MultipartFile file,
      @RequestParam(required = false) String name,
      @RequestParam(defaultValue = "en") String languageCode) {
    return resumes.upload(currentUser.require(authentication).id(), file, name, languageCode);
  }

  @GetMapping("/master-resumes")
  List<MasterResume> list(Authentication authentication) {
    return resumes.resumes(currentUser.require(authentication).id());
  }

  @GetMapping("/master-resumes/{id}")
  MasterResume get(Authentication authentication, @PathVariable UUID id) {
    return resumes.resume(currentUser.require(authentication).id(), id);
  }

  @PostMapping("/master-resumes/{id}/extract")
  MasterResume extract(Authentication authentication, @PathVariable UUID id) {
    return resumes.resume(currentUser.require(authentication).id(), id);
  }

  @PostMapping("/master-resumes/{id}/activate")
  MasterResume activate(Authentication authentication, @PathVariable UUID id) {
    return resumes.activate(currentUser.require(authentication).id(), id);
  }

  @PutMapping("/master-resumes/{id}/language")
  MasterResume updateLanguage(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody ResumeLanguageInput input) {
    return resumes.updateLanguage(
        currentUser.require(authentication).id(), id, input.languageCode());
  }

  @GetMapping("/candidate-facts")
  List<CandidateFact> facts(
      Authentication authentication, @RequestParam(required = false) String status) {
    return resumes.facts(currentUser.require(authentication).id(), status);
  }

  @PostMapping("/candidate-facts")
  @ResponseStatus(HttpStatus.CREATED)
  CandidateFact createFact(
      Authentication authentication, @Valid @RequestBody CandidateFactInput input) {
    return resumes.createFact(currentUser.require(authentication).id(), input);
  }

  @PutMapping("/candidate-facts/{id}")
  CandidateFact editFact(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody CandidateFactInput input) {
    return resumes.editFact(currentUser.require(authentication).id(), id, input);
  }

  @PostMapping("/candidate-facts/{id}/verify")
  CandidateFact verify(Authentication authentication, @PathVariable UUID id) {
    return resumes.transition(currentUser.require(authentication).id(), id, "VERIFIED");
  }

  @PostMapping("/candidate-facts/{id}/reject")
  CandidateFact reject(Authentication authentication, @PathVariable UUID id) {
    return resumes.transition(currentUser.require(authentication).id(), id, "REJECTED");
  }

  @PostMapping("/candidate-facts/{id}/clarify")
  CandidateFact clarify(Authentication authentication, @PathVariable UUID id) {
    return resumes.transition(currentUser.require(authentication).id(), id, "NEEDS_CLARIFICATION");
  }
}

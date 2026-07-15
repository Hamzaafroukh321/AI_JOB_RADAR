package com.aijobradar.matching.api;

import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.matching.application.MatchResult;
import com.aijobradar.matching.application.MatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/match")
public class MatchController {
  private final CurrentUserService currentUser;
  private final MatchService matches;

  public MatchController(CurrentUserService currentUser, MatchService matches) {
    this.currentUser = currentUser;
    this.matches = matches;
  }

  @GetMapping
  MatchResult get(Authentication authentication, @PathVariable UUID jobId) {
    return matches.getOrCompute(currentUser.require(authentication).id(), jobId, false);
  }

  @PostMapping("/recompute")
  MatchResult recompute(Authentication authentication, @PathVariable UUID jobId) {
    return matches.getOrCompute(currentUser.require(authentication).id(), jobId, true);
  }

  @PostMapping("/feedback")
  Map<String, Boolean> feedback(
      Authentication authentication,
      @PathVariable UUID jobId,
      @Valid @RequestBody FeedbackInput input) {
    matches.feedback(
        currentUser.require(authentication).id(), jobId, input.feedbackType(), input.note());
    return Map.of("recorded", true);
  }

  public record FeedbackInput(@NotBlank String feedbackType, @Size(max = 1000) String note) {}
}

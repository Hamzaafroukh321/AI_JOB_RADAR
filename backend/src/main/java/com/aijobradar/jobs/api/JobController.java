package com.aijobradar.jobs.api;

import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.jobs.api.JobModels.JobDetail;
import com.aijobradar.jobs.api.JobModels.JobPage;
import com.aijobradar.jobs.api.JobModels.JobState;
import com.aijobradar.jobs.application.JobAnalysisService;
import com.aijobradar.jobs.application.JobQueryService;
import com.aijobradar.jobs.application.JobQueryService.Search;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
  private final CurrentUserService currentUser;
  private final JobQueryService jobs;
  private final JobAnalysisService analyses;

  public JobController(
      CurrentUserService currentUser, JobQueryService jobs, JobAnalysisService analyses) {
    this.currentUser = currentUser;
    this.jobs = jobs;
    this.analyses = analyses;
  }

  @GetMapping
  JobPage search(
      Authentication authentication,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String section,
      @RequestParam(required = false) String aiRelevance,
      @RequestParam(required = false) String annotationRelevance,
      @RequestParam(required = false) String workplaceMode,
      @RequestParam(required = false) String remoteScope,
      @RequestParam(required = false) String focus,
      @RequestParam(required = false) String experienceLevel,
      @RequestParam(defaultValue = "false") boolean saved,
      @RequestParam(defaultValue = "false") boolean includeHidden,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(defaultValue = "NEWEST") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    UUID user = currentUser.require(authentication).id();
    return jobs.search(
        user,
        new Search(
            q,
            section,
            aiRelevance,
            annotationRelevance,
            workplaceMode,
            remoteScope,
            focus,
            experienceLevel,
            saved,
            includeHidden,
            includeArchived,
            sort,
            page,
            size));
  }

  @GetMapping("/{id}")
  JobDetail detail(Authentication authentication, @PathVariable UUID id) {
    return jobs.detail(currentUser.require(authentication).id(), id);
  }

  @PostMapping("/{id}/save")
  JobState save(Authentication a, @PathVariable UUID id) {
    return state(a, id, "save");
  }

  @DeleteMapping("/{id}/save")
  JobState unsave(Authentication a, @PathVariable UUID id) {
    return state(a, id, "unsave");
  }

  @PostMapping("/{id}/hide")
  JobState hide(Authentication a, @PathVariable UUID id) {
    return state(a, id, "hide");
  }

  @PostMapping("/{id}/restore")
  JobState restore(Authentication a, @PathVariable UUID id) {
    return state(a, id, "restore");
  }

  @PostMapping("/{id}/archive")
  JobState archive(Authentication a, @PathVariable UUID id) {
    return state(a, id, "archive");
  }

  @PostMapping("/{id}/reanalyze")
  Map<String, UUID> reanalyze(Authentication authentication, @PathVariable UUID id) {
    return Map.of("analysisId", analyses.reanalyze(id, currentUser.require(authentication).id()));
  }

  private JobState state(Authentication authentication, UUID id, String action) {
    return jobs.state(currentUser.require(authentication).id(), id, action);
  }
}

package com.aijobradar.applications.api;

import com.aijobradar.applications.application.ApplicationService;
import com.aijobradar.applications.application.ApplicationService.Application;
import com.aijobradar.applications.application.ApplicationService.Event;
import com.aijobradar.applications.application.ApplicationService.OpenResult;
import com.aijobradar.applications.application.ApplicationService.Reminder;
import com.aijobradar.applications.application.ApplicationStateMachine.State;
import com.aijobradar.auth.application.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {
  private final CurrentUserService currentUser;
  private final ApplicationService applications;

  public ApplicationController(CurrentUserService currentUser, ApplicationService applications) {
    this.currentUser = currentUser;
    this.applications = applications;
  }

  @PostMapping("/jobs/{jobId}/application/open")
  OpenResult open(Authentication authentication, @PathVariable UUID jobId) {
    return applications.open(currentUser.require(authentication).id(), jobId);
  }

  @PostMapping("/jobs/{jobId}/application/applied")
  Application applied(
      Authentication authentication,
      @PathVariable UUID jobId,
      @Valid @RequestBody AppliedRequest request) {
    return applications.markApplied(
        currentUser.require(authentication).id(), jobId, request.resumeId(), request.confirmed());
  }

  @PostMapping("/applications/{id}/not-applied")
  Application notApplied(
      Authentication authentication, @PathVariable UUID id, @RequestBody Confirmation request) {
    return applications.markNotApplied(
        currentUser.require(authentication).id(), id, request.confirmed());
  }

  @PostMapping("/applications/{id}/state")
  Application transition(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody StateRequest request) {
    return applications.transition(currentUser.require(authentication).id(), id, request.state());
  }

  @GetMapping("/applications")
  List<Application> list(Authentication authentication) {
    return applications.list(currentUser.require(authentication).id());
  }

  @GetMapping("/applications/{id}/events")
  List<Event> events(Authentication authentication, @PathVariable UUID id) {
    return applications.events(currentUser.require(authentication).id(), id);
  }

  @PostMapping("/applications/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  Event note(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody NoteRequest request) {
    return applications.note(currentUser.require(authentication).id(), id, request.note());
  }

  @PostMapping("/applications/{id}/reminders")
  @ResponseStatus(HttpStatus.CREATED)
  Reminder reminder(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody ReminderRequest request) {
    return applications.reminder(
        currentUser.require(authentication).id(), id, request.remindAt(), request.message());
  }

  @GetMapping("/applications/export.csv")
  ResponseEntity<byte[]> export(Authentication authentication) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/csv"));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename("applications.csv").build());
    return new ResponseEntity<>(
        applications.exportCsv(currentUser.require(authentication).id()), headers, HttpStatus.OK);
  }

  public record AppliedRequest(@NotNull UUID resumeId, boolean confirmed) {}

  public record Confirmation(boolean confirmed) {}

  public record StateRequest(@NotNull State state) {}

  public record NoteRequest(@NotBlank String note) {}

  public record ReminderRequest(@NotNull OffsetDateTime remindAt, @NotBlank String message) {}
}

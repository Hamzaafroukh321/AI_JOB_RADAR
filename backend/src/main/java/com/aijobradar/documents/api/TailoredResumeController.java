package com.aijobradar.documents.api;

import com.aijobradar.auth.application.CurrentUserService;
import com.aijobradar.documents.application.ResumeContent;
import com.aijobradar.documents.application.TailoredResumeService;
import com.aijobradar.documents.application.TailoredResumeService.TailoredResume;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TailoredResumeController {
  private final CurrentUserService currentUser;
  private final TailoredResumeService resumes;

  public TailoredResumeController(CurrentUserService currentUser, TailoredResumeService resumes) {
    this.currentUser = currentUser;
    this.resumes = resumes;
  }

  @GetMapping("/tailored-resume-variants")
  Set<String> variants() {
    return resumes.variants();
  }

  @PostMapping("/jobs/{jobId}/tailored-resumes")
  @ResponseStatus(HttpStatus.CREATED)
  TailoredResume generate(
      Authentication authentication,
      @PathVariable UUID jobId,
      @Valid @RequestBody GenerateRequest request) {
    return resumes.generate(currentUser.require(authentication).id(), jobId, request.variant());
  }

  @GetMapping("/tailored-resumes")
  List<TailoredResume> list(Authentication authentication) {
    return resumes.list(currentUser.require(authentication).id());
  }

  @GetMapping("/tailored-resumes/{id}")
  TailoredResume get(Authentication authentication, @PathVariable UUID id) {
    return resumes.get(currentUser.require(authentication).id(), id);
  }

  @PutMapping("/tailored-resumes/{id}")
  TailoredResume revise(
      Authentication authentication, @PathVariable UUID id, @RequestBody ResumeContent content) {
    return resumes.revise(currentUser.require(authentication).id(), id, content);
  }

  @PostMapping("/tailored-resumes/{id}/approve")
  TailoredResume approve(Authentication authentication, @PathVariable UUID id) {
    return resumes.approve(currentUser.require(authentication).id(), id);
  }

  @GetMapping("/tailored-resumes/{id}/download")
  ResponseEntity<byte[]> download(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "pdf") String format) {
    var download = resumes.download(currentUser.require(authentication).id(), id, format);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(download.contentType()));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(download.filename()).build());
    return new ResponseEntity<>(download.content(), headers, HttpStatus.OK);
  }

  public record GenerateRequest(@NotBlank String variant) {}
}

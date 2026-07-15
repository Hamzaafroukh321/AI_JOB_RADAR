package com.aijobradar.documents.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ResumeVersionPolicy {
  public void requireEditable(String status) {
    if ("LOCKED".equals(status))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Locked resume versions cannot be edited");
  }
}

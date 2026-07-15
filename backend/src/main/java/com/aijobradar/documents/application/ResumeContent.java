package com.aijobradar.documents.application;

import java.util.List;
import java.util.UUID;

public record ResumeContent(
    Claim headline, Claim summary, List<Claim> highlights, List<String> missingRequirements) {
  public record Claim(String text, List<UUID> verifiedFactIds) {}
}

package com.aijobradar.documents.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ResumeFactValidator {
  public void validate(ResumeContent content, Set<UUID> verifiedFactIds) {
    if (content == null) throw new IllegalArgumentException("Resume content is required");
    List<ResumeContent.Claim> claims =
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(content.headline(), content.summary()),
                content.highlights().stream())
            .toList();
    if (claims.isEmpty()) throw new IllegalArgumentException("At least one claim is required");
    for (ResumeContent.Claim claim : claims) {
      if (claim == null || claim.text() == null || claim.text().isBlank())
        throw new IllegalArgumentException("Resume claims cannot be blank");
      Set<UUID> citations =
          claim.verifiedFactIds() == null ? Set.of() : new LinkedHashSet<>(claim.verifiedFactIds());
      if (citations.isEmpty())
        throw new IllegalArgumentException("Every resume claim must cite a verified fact");
      if (!verifiedFactIds.containsAll(citations))
        throw new IllegalArgumentException("Resume contains an unsupported candidate claim");
    }
  }
}

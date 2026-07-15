package com.aijobradar.profile.application;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UploadPolicy {
  public ValidatedUpload validate(
      String originalFilename, String suppliedMimeType, byte[] content) {
    if (content.length == 0) throw new IllegalArgumentException("Resume file is empty");
    String filename = sanitizeFilename(originalFilename);
    String extension = StringUtils.getFilenameExtension(filename);
    extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    String detected = detect(content);
    boolean matching =
        ("pdf".equals(extension) && "application/pdf".equals(detected))
            || ("docx".equals(extension)
                && "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    .equals(detected));
    if (!matching)
      throw new IllegalArgumentException("Only valid PDF and DOCX resumes are accepted");
    if (StringUtils.hasText(suppliedMimeType)
        && !"application/octet-stream".equals(suppliedMimeType)
        && !detected.equals(suppliedMimeType)) {
      throw new IllegalArgumentException("Resume extension, content, and MIME type do not match");
    }
    return new ValidatedUpload(filename, detected);
  }

  public String sanitizeFilename(String originalFilename) {
    String leaf = StringUtils.getFilename(originalFilename == null ? "resume" : originalFilename);
    String sanitized = leaf.replaceAll("[^A-Za-z0-9._ -]", "_").replaceAll("\\s+", " ").trim();
    if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) return "resume";
    return sanitized.length() > 180 ? sanitized.substring(sanitized.length() - 180) : sanitized;
  }

  private String detect(byte[] content) {
    if (content.length >= 5
        && content[0] == '%'
        && content[1] == 'P'
        && content[2] == 'D'
        && content[3] == 'F'
        && content[4] == '-') return "application/pdf";
    if (content.length >= 4
        && content[0] == 'P'
        && content[1] == 'K'
        && content[2] == 3
        && content[3] == 4)
      return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    throw new IllegalArgumentException("Resume content signature is invalid");
  }

  public record ValidatedUpload(String filename, String mimeType) {}
}

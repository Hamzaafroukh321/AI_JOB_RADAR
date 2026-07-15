package com.aijobradar.jobs.application;

import com.aijobradar.sources.application.JobSourceConnector.RawJobRecord;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class JobContentNormalizer {
  private static final int MAX_DESCRIPTION_CHARS = 100_000;
  private static final Pattern SALARY =
      Pattern.compile(
          "(?i)(USD|EUR|GBP|MAD|\\$|€|£)\\s*([0-9][0-9,.]*)(?:\\s*[-–]\\s*(?:USD|EUR|GBP|MAD|\\$|€|£)?\\s*([0-9][0-9,.]*))?");
  private static final Set<String> TRACKING_PARAMETERS =
      Set.of(
          "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "fbclid", "gclid");

  public NormalizedJob normalize(RawJobRecord raw) {
    String title = text(raw.rawTitle(), 500);
    String company = text(raw.rawCompany(), 300);
    if (title.isBlank() || company.isBlank())
      throw new IllegalArgumentException("Job title and company are required");
    String sanitizedHtml = sanitizeHtml(raw.rawDescription());
    String description = text(Jsoup.parse(sanitizedHtml).text(), MAX_DESCRIPTION_CHARS);
    if (description.isBlank()) throw new IllegalArgumentException("Job description is required");
    String location = text(raw.rawLocation(), 500);
    String canonicalTitle = canonicalTitle(title);
    String companyNormalized = company(company);
    String fingerprint =
        hash(
            companyNormalized
                + "\n"
                + canonicalTitle
                + "\n"
                + fold(location)
                + "\n"
                + hash(description));
    Salary salary = salary(description);
    return new NormalizedJob(
        title,
        canonicalTitle,
        company,
        companyNormalized,
        sanitizedHtml,
        description,
        location,
        canonicalUrl(raw.sourceUrl()),
        canonicalUrl(raw.applicationUrl()),
        raw.sourcePostedAt(),
        raw.sourceUpdatedAt(),
        normalizeEmployment(raw.employmentType()),
        normalizeWorkplace(raw.workplaceMode(), description),
        salary,
        fingerprint);
  }

  public String sanitizeHtml(String value) {
    String bounded =
        value == null ? "" : value.substring(0, Math.min(value.length(), MAX_DESCRIPTION_CHARS));
    return Jsoup.clean(bounded, Safelist.basic());
  }

  String canonicalTitle(String value) {
    String title = text(value, 500);
    title = title.replaceAll("(?i)\\bGenAI\\b", "Generative AI");
    title = title.replaceAll("(?i)\\bML\\b", "Machine Learning");
    title = title.replaceAll("(?i)\\bNLP\\b", "Natural Language Processing");
    title = title.replaceAll("(?i)\\bSWE\\b", "Software Engineer");
    return title;
  }

  String company(String value) {
    return fold(value)
        .replaceAll(
            "(?i)\\b(incorporated|inc|llc|ltd|limited|corp|corporation|gmbh|sarl|sa)\\b", "")
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  String canonicalUrl(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      URI source = URI.create(value.trim());
      if (!("http".equalsIgnoreCase(source.getScheme())
          || "https".equalsIgnoreCase(source.getScheme()))) return null;
      String query =
          source.getRawQuery() == null
              ? null
              : Arrays.stream(source.getRawQuery().split("&"))
                  .filter(
                      item -> {
                        String key = item.split("=", 2)[0].toLowerCase(Locale.ROOT);
                        return !TRACKING_PARAMETERS.contains(key);
                      })
                  .collect(Collectors.joining("&"));
      return new URI(
              source.getScheme().toLowerCase(Locale.ROOT),
              null,
              source.getHost().toLowerCase(Locale.ROOT),
              source.getPort(),
              source.getPath(),
              query == null || query.isBlank() ? null : query,
              null)
          .toString();
    } catch (Exception exception) {
      return null;
    }
  }

  Salary salary(String description) {
    Matcher matcher = SALARY.matcher(description);
    if (!matcher.find()) return new Salary(null, null, null, null);
    String token = matcher.group(1).toUpperCase(Locale.ROOT);
    String currency =
        switch (token) {
          case "$", "USD" -> "USD";
          case "€", "EUR" -> "EUR";
          case "£", "GBP" -> "GBP";
          default -> "MAD";
        };
    BigDecimal min = amount(matcher.group(2));
    BigDecimal max = matcher.group(3) == null ? null : amount(matcher.group(3));
    String nearby =
        description
            .substring(matcher.start(), Math.min(description.length(), matcher.end() + 30))
            .toLowerCase(Locale.ROOT);
    String period =
        nearby.matches(".*(per year|annual|annually|/year|/yr).*")
            ? "YEAR"
            : nearby.matches(".*(per hour|hourly|/hour|/hr).*") ? "HOUR" : null;
    return new Salary(min, max, currency, period);
  }

  private BigDecimal amount(String value) {
    String normalized = value.replace(",", "");
    return new BigDecimal(normalized);
  }

  private String normalizeEmployment(String value) {
    if (value == null) return "UNKNOWN";
    String normalized = value.toLowerCase(Locale.ROOT);
    if (normalized.contains("full")) return "FULL_TIME";
    if (normalized.contains("part")) return "PART_TIME";
    if (normalized.contains("contract")) return "CONTRACT";
    if (normalized.contains("freelance")) return "FREELANCE";
    if (normalized.contains("intern")) return "INTERNSHIP";
    return "UNKNOWN";
  }

  private String normalizeWorkplace(String value, String description) {
    String normalized = (value == null ? "" : value) + " " + description;
    normalized = normalized.toLowerCase(Locale.ROOT);
    if (normalized.contains("hybrid")) return "HYBRID";
    if (normalized.contains("remote")) return "REMOTE";
    if (normalized.contains("on-site") || normalized.contains("onsite")) return "ONSITE";
    return "UNKNOWN";
  }

  private String text(String value, int limit) {
    if (value == null) return "";
    String normalized = Normalizer.normalize(Jsoup.parse(value).text(), Normalizer.Form.NFKC);
    normalized = normalized.replaceAll("\\s+", " ").trim();
    return normalized.substring(0, Math.min(normalized.length(), limit));
  }

  private String fold(String value) {
    return Normalizer.normalize(text(value, 2000), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  public record Salary(BigDecimal min, BigDecimal max, String currency, String period) {}

  public record NormalizedJob(
      String originalTitle,
      String canonicalTitle,
      String companyName,
      String companyNormalized,
      String descriptionHtml,
      String descriptionText,
      String rawLocation,
      String sourceUrl,
      String applicationUrl,
      Instant sourcePostedAt,
      Instant sourceUpdatedAt,
      String employmentType,
      String workplaceMode,
      Salary salary,
      String fingerprint) {}
}

package com.aijobradar.jobs.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DeterministicJobAnalyzer {
  private static final List<String> AI_SIGNALS =
      List.of(
          "machine learning",
          " ml ",
          "artificial intelligence",
          "generative ai",
          "genai",
          " llm",
          "rag",
          "natural language processing",
          "nlp",
          "computer vision",
          "mlops",
          "spring ai",
          "langchain",
          "model training",
          "prompt engineer");
  private static final List<String> ANNOTATION_SIGNALS =
      List.of(
          "data annotation",
          "data labeling",
          "model evaluation",
          "evaluate ai",
          "llm response",
          "coding evaluator",
          "prompt writing",
          "rubric",
          "search rater",
          "safety evaluation",
          "fact checking");
  private static final List<String> FALSE_POSITIVE =
      List.of(
          "sales representative",
          "account executive",
          "marketing manager",
          "sell ai",
          "annotation software sales");
  private static final List<String> TECHNOLOGIES =
      List.of(
          "Java",
          "Spring Boot",
          "Spring AI",
          "Angular",
          "Python",
          "SQL",
          "LangChain",
          "RAG",
          "Kubernetes",
          "AWS",
          "PyTorch",
          "TensorFlow",
          "LLM");
  private static final Pattern COUNTRY_CODES =
      Pattern.compile(
          "(?i)\\b(US|USA|United States|Canada|France|Germany|UK|United Kingdom|Morocco|Maroc)\\b");

  public JobAnalysis analyze(
      String title, String description, String employmentType, String workplaceMode) {
    String content = neutralize(" " + title + " " + description + " ").toLowerCase(Locale.ROOT);
    int ai = signals(content, AI_SIGNALS);
    int annotation = signals(content, ANNOTATION_SIGNALS);
    boolean negative = FALSE_POSITIVE.stream().anyMatch(content::contains);
    String aiRelevance = negative ? "NONE" : relevance(ai);
    String annotationRelevance = negative ? "NONE" : relevance(annotation);
    String workplace = workplace(workplaceMode, content);
    RegionDecision region = region(content, workplace);
    List<String> technologies =
        TECHNOLOGIES.stream().filter(item -> containsTerm(content, item)).toList();
    String role = annotation > ai ? "AI_TRAINING_DATA" : ai > 0 ? "AI_ENGINEERING" : "OTHER";
    List<String> warnings = new ArrayList<>();
    if ("REMOTE".equals(workplace) && "UNKNOWN".equals(region.remoteScope()))
      warnings.add(
          "Remote scope is not stated; this job is excluded from strict worldwide remote.");
    if (negative) warnings.add("AI language appears secondary to non-target duties.");
    return new JobAnalysis(
        summary(title, description),
        role,
        List.of(),
        aiRelevance,
        annotationRelevance,
        seniority(content),
        List.of(employmentType == null ? "UNKNOWN" : employmentType),
        workplace,
        region.remoteScope(),
        region.allowedCountries(),
        region.excludedCountries(),
        region.allowedRegions(),
        region.timezones(),
        region.moroccoEligibility(),
        responsibilities(description),
        List.of(),
        List.of(),
        technologies,
        List.of(),
        years(content),
        null,
        sponsorship(content),
        List.of(),
        clearance(content),
        warnings,
        (ai > 0 || annotation > 0) ? 0.82 : 0.65);
  }

  private RegionDecision region(String content, String workplace) {
    if (!"REMOTE".equals(workplace)) {
      boolean morocco =
          containsAny(
              content, "morocco", "maroc", "casablanca", "rabat", "marrakech", "tangier", "agadir");
      return new RegionDecision(
          "UNKNOWN",
          List.of(),
          List.of(),
          geographicRegions(content),
          List.of(),
          morocco ? "ELIGIBLE" : "UNKNOWN");
    }
    boolean worldwide =
        containsAny(
            content,
            "worldwide",
            "work from anywhere",
            "anywhere in the world",
            "globally remote",
            "global remote");
    boolean morocco = containsAny(content, "morocco", "maroc");
    boolean broadRegion = containsAny(content, "emea", "mena", "africa", "north africa");
    boolean restricted =
        containsAny(
            content,
            "us only",
            "u.s. only",
            "united states only",
            "remote within the eu",
            "eu only",
            "must reside in",
            "uk only",
            "canada only");
    List<String> countries = countries(content);
    if (restricted)
      return new RegionDecision(
          "COUNTRY_LIST",
          countries,
          List.of("MA"),
          geographicRegions(content),
          List.of(),
          "INELIGIBLE");
    if (worldwide)
      return new RegionDecision(
          "WORLDWIDE", List.of(), List.of(), List.of("WORLDWIDE"), List.of(), "ELIGIBLE");
    if (morocco)
      return new RegionDecision(
          "COUNTRY_LIST",
          List.of("MA"),
          List.of(),
          geographicRegions(content),
          List.of(),
          "ELIGIBLE");
    if (broadRegion)
      return new RegionDecision(
          "REGION_LIST", List.of(), List.of(), regions(content), List.of(), "POSSIBLY_ELIGIBLE");
    if (content.contains("utc") || content.contains("gmt"))
      return new RegionDecision(
          "TIMEZONE_RESTRICTED",
          List.of(),
          List.of(),
          List.of(),
          List.of(evidence(content, "utc", "gmt")),
          "UNKNOWN");
    return new RegionDecision("UNKNOWN", countries, List.of(), List.of(), List.of(), "UNKNOWN");
  }

  private List<String> countries(String content) {
    Set<String> result = new LinkedHashSet<>();
    Matcher matcher = COUNTRY_CODES.matcher(content);
    while (matcher.find()) {
      String value = matcher.group().toLowerCase(Locale.ROOT);
      if (value.contains("morocco") || value.contains("maroc")) result.add("MA");
      else if (value.contains("canada")) result.add("CA");
      else if (value.contains("france")) result.add("FR");
      else if (value.contains("germany")) result.add("DE");
      else if (value.contains("uk") || value.contains("kingdom")) result.add("GB");
      else result.add("US");
    }
    return List.copyOf(result);
  }

  private List<String> regions(String content) {
    List<String> result = new ArrayList<>();
    for (String region : List.of("EMEA", "MENA", "AFRICA", "NORTH AFRICA"))
      if (content.contains(region.toLowerCase(Locale.ROOT))) result.add(region.replace(' ', '_'));
    return result;
  }

  private List<String> geographicRegions(String content) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    if (containsAny(
        content, "morocco", "maroc", "casablanca", "rabat", "marrakech", "tangier", "agadir"))
      result.add("MOROCCO");
    if (containsAny(
        content,
        "france",
        "germany",
        "united kingdom",
        " uk ",
        "spain",
        "italy",
        "netherlands",
        "switzerland",
        "europe",
        " eu ")) result.add("EUROPE");
    if (containsAny(
        content,
        "united arab emirates",
        " uae ",
        "dubai",
        "abu dhabi",
        "saudi arabia",
        "qatar",
        "bahrain",
        "kuwait",
        "oman",
        "jordan",
        "lebanon",
        "middle east",
        "mena")) result.add("MIDDLE_EAST");
    return List.copyOf(result);
  }

  private int signals(String value, List<String> signals) {
    return (int) signals.stream().filter(value::contains).count();
  }

  private String relevance(int count) {
    return count >= 3 ? "HIGH" : count == 2 ? "MEDIUM" : count == 1 ? "LOW" : "NONE";
  }

  private String workplace(String supplied, String content) {
    if (supplied != null && !"UNKNOWN".equals(supplied)) return supplied;
    if (content.contains("hybrid")) return "HYBRID";
    if (content.contains("remote")) return "REMOTE";
    if (content.contains("onsite") || content.contains("on-site")) return "ONSITE";
    return "UNKNOWN";
  }

  private String seniority(String content) {
    if (containsAny(content, "staff ", "principal ")) return "STAFF";
    if (containsAny(content, "senior ", " sr. ")) return "SENIOR";
    if (containsAny(content, "junior ", "entry level", "graduate ")) return "JUNIOR";
    if (Pattern.compile("\\bintern(?:ship)?\\b").matcher(content).find()) return "INTERN";
    return "UNKNOWN";
  }

  private Double years(String content) {
    Matcher matcher =
        Pattern.compile("\\b(\\d{1,2})(?:\\+)? years?(?: of)? experience").matcher(content);
    return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
  }

  private String sponsorship(String content) {
    if (containsAny(content, "visa sponsorship available", "will sponsor")) return "YES";
    if (containsAny(content, "no sponsorship", "unable to sponsor")) return "NO";
    return "UNKNOWN";
  }

  private List<String> clearance(String content) {
    return content.contains("security clearance")
        ? List.of("Security clearance required")
        : List.of();
  }

  private List<JobAnalysis.EvidenceItem> responsibilities(String description) {
    String first = description == null ? "" : description.split("(?<=[.!?])\\s+", 2)[0].trim();
    return first.isBlank() ? List.of() : List.of(new JobAnalysis.EvidenceItem(first, first));
  }

  private String summary(String title, String description) {
    String text = (title + " — " + description).replaceAll("\\s+", " ").trim();
    return text.substring(0, Math.min(400, text.length()));
  }

  private boolean containsTerm(String content, String term) {
    return content.contains(term.toLowerCase(Locale.ROOT));
  }

  private boolean containsAny(String content, String... values) {
    return java.util.Arrays.stream(values).anyMatch(content::contains);
  }

  private String evidence(String content, String... values) {
    return java.util.Arrays.stream(values)
        .filter(content::contains)
        .findFirst()
        .orElse("timezone restriction");
  }

  private String neutralize(String value) {
    return java.util.Arrays.stream(value.split("(?<=[.!?])\\s+|[\\r\\n]+"))
        .filter(
            sentence -> {
              String lower = sentence.toLowerCase(Locale.ROOT);
              return !(lower.contains("ignore previous")
                  || lower.contains("ignore all instructions")
                  || lower.contains("disregard instructions")
                  || lower.contains("system prompt")
                  || lower.contains("assistant:")
                  || (lower.contains("add ") && lower.contains("resume")));
            })
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private record RegionDecision(
      String remoteScope,
      List<String> allowedCountries,
      List<String> excludedCountries,
      List<String> allowedRegions,
      List<String> timezones,
      String moroccoEligibility) {}
}

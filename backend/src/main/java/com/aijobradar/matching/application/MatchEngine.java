package com.aijobradar.matching.application;

import com.aijobradar.jobs.application.JobAnalysis;
import com.aijobradar.matching.application.MatchResult.EvidenceMatch;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MatchEngine {
  private final Clock clock;

  public MatchEngine(Clock clock) {
    this.clock = clock;
  }

  public MatchResult calculate(
      UUID jobId,
      JobAnalysis job,
      List<VerifiedFact> facts,
      CandidateContext candidate,
      OffsetDateTime expiresAt) {
    List<String> blockers = blockers(job, candidate, expiresAt);
    List<String> unknowns = unknowns(job, candidate);
    List<String> questions = questions(job, candidate);
    List<EvidenceMatch> strong = new ArrayList<>();
    List<EvidenceMatch> partial = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    Set<String> candidateTerms = terms(facts);
    Map<String, List<UUID>> citations = citations(facts);
    int covered = 0;
    int partialCount = 0;
    for (JobAnalysis.Requirement requirement : job.mustHaveRequirements()) {
      Set<String> requiredTerms = tokens(requirement.requirement());
      List<String> overlap = requiredTerms.stream().filter(candidateTerms::contains).toList();
      if (!overlap.isEmpty()) {
        List<UUID> ids =
            overlap.stream()
                .flatMap(term -> citations.getOrDefault(term, List.of()).stream())
                .distinct()
                .toList();
        if (!ids.isEmpty()) {
          double ratio = (double) overlap.size() / Math.max(1, requiredTerms.size());
          EvidenceMatch evidence =
              new EvidenceMatch(requirement.requirement(), requirement.evidence(), ids);
          if (ratio >= 0.5) {
            strong.add(evidence);
            covered++;
          } else {
            partial.add(evidence);
            partialCount++;
          }
          continue;
        }
      }
      missing.add(requirement.requirement());
    }
    for (String technology : job.technologies()) {
      List<UUID> ids = citations.getOrDefault(technology.toLowerCase(Locale.ROOT), List.of());
      if (!ids.isEmpty()
          && strong.stream().noneMatch(item -> item.label().equalsIgnoreCase(technology)))
        strong.add(new EvidenceMatch(technology, technology + " is mentioned in the job", ids));
    }
    boolean annotation =
        "AI_TRAINING_DATA".equals(job.primaryRoleFamily())
            || !"NONE".equals(job.annotationRelevance());
    int requirementCoverage =
        job.mustHaveRequirements().isEmpty()
            ? evidenceScore(strong)
            : percent(covered + partialCount * 0.5, job.mustHaveRequirements().size());
    int experience = evidenceScore(strong);
    int role = roleScore(job, facts, annotation);
    int domain = domainScore(facts, annotation);
    int seniority = seniorityScore(job.seniority(), facts);
    int location = locationScore(job, candidate);
    int preferences = preferenceScore(job, candidate);
    Map<String, Integer> components = new LinkedHashMap<>();
    int score;
    if (annotation) {
      components.put("aiTrainingEvidence", domain);
      components.put("taskTypeMatch", role);
      components.put("technicalDomainMatch", requirementCoverage);
      components.put("languageMatch", candidate.languagesVerified() ? 60 : 0);
      components.put("qualityReviewEvidence", experience);
      components.put("locationEligibility", location);
      components.put("schedulePreference", preferences);
      score =
          weighted(
              components,
              Map.of(
                  "aiTrainingEvidence", 25,
                  "taskTypeMatch", 20,
                  "technicalDomainMatch", 20,
                  "languageMatch", 10,
                  "qualityReviewEvidence", 10,
                  "locationEligibility", 10,
                  "schedulePreference", 5));
    } else {
      components.put("requiredSkillCoverage", requirementCoverage);
      components.put("experienceEvidence", experience);
      components.put("roleRelevance", role);
      components.put("aiDomainRelevance", domain);
      components.put("seniorityFit", seniority);
      components.put("locationEligibility", location);
      components.put(
          "educationFit",
          unknowns.stream().anyMatch(value -> value.contains("education")) ? 0 : 50);
      components.put("preferences", preferences);
      score =
          weighted(
              components,
              Map.of(
                  "requiredSkillCoverage", 25,
                  "experienceEvidence", 20,
                  "roleRelevance", 15,
                  "aiDomainRelevance", 10,
                  "seniorityFit", 10,
                  "locationEligibility", 10,
                  "educationFit", 5,
                  "preferences", 5));
    }
    String eligibility = eligibility(blockers, unknowns, job, candidate);
    score = cap(score, eligibility, expiresAt);
    double confidence =
        Math.max(0.2, Math.min(0.98, 0.45 + facts.size() * 0.04 - unknowns.size() * 0.05));
    String action =
        !blockers.isEmpty()
            ? "Review the blockers before proceeding."
            : unknowns.isEmpty()
                ? "Review the evidence and consider applying externally."
                : "Answer the eligibility questions before deciding.";
    String rationale =
        score
            + "/100 with "
            + strong.size()
            + " verified evidence match(es); eligibility is "
            + eligibility
            + ".";
    return new MatchResult(
        null,
        jobId,
        eligibility,
        score,
        confidence,
        Map.copyOf(components),
        List.copyOf(strong),
        List.copyOf(partial),
        List.copyOf(missing),
        List.copyOf(unknowns),
        List.copyOf(blockers),
        List.copyOf(questions),
        action,
        rationale);
  }

  private List<String> blockers(
      JobAnalysis job, CandidateContext candidate, OffsetDateTime expiresAt) {
    List<String> result = new ArrayList<>();
    if (expiresAt != null && expiresAt.isBefore(now())) result.add("JOB_EXPIRED");
    if ("INELIGIBLE".equals(job.moroccoEligibility()) && !candidate.relocationAllowed())
      result.add("REMOTE_SCOPE_EXCLUDES_MOROCCO");
    if (!job.citizenshipRequirements().isEmpty())
      result.add("CITIZENSHIP_REQUIREMENT_NOT_CONFIRMED");
    if (!job.securityClearanceRequirements().isEmpty())
      result.add("SECURITY_CLEARANCE_NOT_CONFIRMED");
    return result;
  }

  private List<String> unknowns(JobAnalysis job, CandidateContext candidate) {
    List<String> result = new ArrayList<>();
    if ("UNKNOWN".equals(job.remoteScope()) && "REMOTE".equals(job.workplaceMode()))
      result.add("Remote geographic scope is unknown");
    if ("UNKNOWN".equals(job.visaSponsorship()) && candidate.sponsorshipRequired())
      result.add("Visa sponsorship is unknown");
    if (!candidate.languagesVerified() && !job.requiredLanguages().isEmpty())
      result.add("Required language fit is unknown until languages are verified");
    if (job.mustHaveRequirements().stream().anyMatch(item -> "EDUCATION".equals(item.type())))
      result.add("Mandatory education equivalence needs review");
    return result;
  }

  private List<String> questions(JobAnalysis job, CandidateContext candidate) {
    List<String> result = new ArrayList<>();
    if ("UNKNOWN".equals(job.remoteScope()) && "REMOTE".equals(job.workplaceMode()))
      result.add("Does the employer allow working from Morocco?");
    if ("UNKNOWN".equals(job.visaSponsorship()) && candidate.sponsorshipRequired())
      result.add("Does this employer sponsor the required work authorization?");
    if (!candidate.languagesVerified() && !job.requiredLanguages().isEmpty())
      result.add("Can you verify the required language and proficiency level?");
    return result;
  }

  private String eligibility(
      List<String> blockers, List<String> unknowns, JobAnalysis job, CandidateContext candidate) {
    if (blockers.contains("JOB_EXPIRED") || blockers.size() > 1) return "INELIGIBLE";
    if (!blockers.isEmpty()) return "LIKELY_INELIGIBLE";
    if (!unknowns.isEmpty()) return "NEEDS_REVIEW";
    if ("ELIGIBLE".equals(job.moroccoEligibility()) || candidate.relocationAllowed())
      return "ELIGIBLE";
    return "LIKELY_ELIGIBLE";
  }

  private int cap(int score, String eligibility, OffsetDateTime expiresAt) {
    if (expiresAt != null && expiresAt.isBefore(now())) return 0;
    return switch (eligibility) {
      case "INELIGIBLE" -> Math.min(score, 25);
      case "LIKELY_INELIGIBLE" -> Math.min(score, 45);
      case "NEEDS_REVIEW" -> Math.min(score, 79);
      default -> score;
    };
  }

  private Set<String> terms(List<VerifiedFact> facts) {
    return facts.stream()
        .flatMap(fact -> tokens(fact.statement() + " " + String.join(" ", fact.skills())).stream())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private Map<String, List<UUID>> citations(List<VerifiedFact> facts) {
    Map<String, List<UUID>> result = new LinkedHashMap<>();
    for (VerifiedFact fact : facts)
      for (String term : tokens(fact.statement() + " " + String.join(" ", fact.skills())))
        result.computeIfAbsent(term, ignored -> new ArrayList<>()).add(fact.id());
    return result;
  }

  private Set<String> tokens(String value) {
    return java.util.Arrays.stream(
            value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.]", " ").split("\\s+"))
        .filter(token -> token.length() > 1)
        .collect(java.util.stream.Collectors.toSet());
  }

  private int evidenceScore(List<EvidenceMatch> evidence) {
    return Math.min(100, evidence.size() * 25);
  }

  private int percent(double value, double total) {
    return (int) Math.round(100 * value / Math.max(1, total));
  }

  private int roleScore(JobAnalysis job, List<VerifiedFact> facts, boolean annotation) {
    String signal = annotation ? "evaluat" : "engineer";
    return facts.stream()
            .anyMatch(fact -> fact.statement().toLowerCase(Locale.ROOT).contains(signal))
        ? 90
        : job.aiRelevance().equals("HIGH") ? 60 : 40;
  }

  private int domainScore(List<VerifiedFact> facts, boolean annotation) {
    String[] signals =
        annotation
            ? new String[] {"llm", "prompt", "review", "evaluation", "annotation"}
            : new String[] {"ai", "llm", "rag", "machine learning", "spring ai"};
    long count =
        facts.stream()
            .filter(
                fact ->
                    java.util.Arrays.stream(signals)
                        .anyMatch(
                            signal -> fact.statement().toLowerCase(Locale.ROOT).contains(signal)))
            .count();
    return Math.min(100, (int) count * 30);
  }

  private int seniorityScore(String seniority, List<VerifiedFact> facts) {
    if (Set.of("SENIOR", "STAFF", "LEAD", "MANAGER").contains(seniority))
      return facts.size() >= 8 ? 65 : 30;
    return 80;
  }

  private int locationScore(JobAnalysis job, CandidateContext candidate) {
    if ("INELIGIBLE".equals(job.moroccoEligibility()) && !candidate.relocationAllowed()) return 0;
    if ("UNKNOWN".equals(job.moroccoEligibility())) return 35;
    return 90;
  }

  private int preferenceScore(JobAnalysis job, CandidateContext candidate) {
    return candidate.preferredWorkplaceModes().isEmpty()
            || candidate.preferredWorkplaceModes().contains(job.workplaceMode())
        ? 80
        : 35;
  }

  private int weighted(Map<String, Integer> scores, Map<String, Integer> weights) {
    return (int)
        Math.round(
            scores.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * weights.get(entry.getKey()) / 100.0)
                .sum());
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  public record VerifiedFact(UUID id, String statement, List<String> skills) {}

  public record CandidateContext(
      boolean relocationAllowed,
      boolean sponsorshipRequired,
      boolean languagesVerified,
      List<String> preferredWorkplaceModes) {}
}

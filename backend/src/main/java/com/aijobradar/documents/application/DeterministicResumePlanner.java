package com.aijobradar.documents.application;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeterministicResumePlanner {
  public static final Set<String> VARIANTS =
      Set.of("AI_ENGINEER", "GENAI_RAG", "JAVA_ANGULAR", "AI_TRAINING", "REMOTE_CONTRACT");

  public ResumeContent plan(
      String variant, String jobTitle, List<Fact> facts, List<String> missing) {
    if (!VARIANTS.contains(variant)) throw new IllegalArgumentException("Unknown resume variant");
    if (facts.isEmpty())
      throw new IllegalArgumentException("Verify at least one candidate fact before generation");
    List<String> signals = signals(variant, jobTitle);
    List<Fact> ordered =
        facts.stream()
            .sorted(
                Comparator.comparingInt((Fact fact) -> overlap(fact, signals))
                    .reversed()
                    .thenComparing(Fact::statement))
            .toList();
    Fact primary = ordered.getFirst();
    List<ResumeContent.Claim> highlights =
        ordered.stream()
            .limit(8)
            .map(fact -> new ResumeContent.Claim(fact.statement(), List.of(fact.id())))
            .toList();
    return new ResumeContent(
        new ResumeContent.Claim(label(variant) + " — " + jobTitle, List.of(primary.id())),
        new ResumeContent.Claim(primary.statement(), List.of(primary.id())),
        highlights,
        List.copyOf(missing));
  }

  private int overlap(Fact fact, List<String> signals) {
    String value =
        (fact.statement() + " " + String.join(" ", fact.skills())).toLowerCase(Locale.ROOT);
    return (int) signals.stream().filter(value::contains).count();
  }

  private List<String> signals(String variant, String jobTitle) {
    String configured =
        switch (variant) {
          case "GENAI_RAG" -> "genai llm rag vector prompt";
          case "JAVA_ANGULAR" -> "java spring angular typescript";
          case "AI_TRAINING" -> "evaluation annotation review quality llm";
          case "REMOTE_CONTRACT" -> "remote contract freelance delivery";
          default -> "ai engineer machine learning software";
        };
    return Arrays.stream((configured + " " + jobTitle).toLowerCase(Locale.ROOT).split("\\s+"))
        .filter(value -> value.length() > 1)
        .toList();
  }

  private String label(String variant) {
    return switch (variant) {
      case "GENAI_RAG" -> "Generative AI and RAG";
      case "JAVA_ANGULAR" -> "Java and Angular Engineer";
      case "AI_TRAINING" -> "AI Training and Evaluation";
      case "REMOTE_CONTRACT" -> "Remote Contract Engineer";
      default -> "AI Engineer";
    };
  }

  public record Fact(UUID id, String statement, List<String> skills) {}
}

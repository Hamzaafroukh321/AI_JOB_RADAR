package com.aijobradar.operations;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OperationalAlertRules {
  public List<Alert> evaluate(
      long sourceFailures, long aiFailures, OperationsProperties properties) {
    List<Alert> alerts = new ArrayList<>();
    if (sourceFailures >= properties.failedSourceAlertThreshold())
      alerts.add(
          new Alert(
              "SOURCE_FAILURES",
              sourceFailures >= properties.failedSourceAlertThreshold() * 2
                  ? "CRITICAL"
                  : "WARNING",
              "Source refresh failures exceeded the configured threshold"));
    if (aiFailures >= properties.failedAiAlertThreshold())
      alerts.add(
          new Alert(
              "AI_FAILURES",
              aiFailures >= properties.failedAiAlertThreshold() * 2 ? "CRITICAL" : "WARNING",
              "AI provider or validation failures exceeded the configured threshold"));
    return List.copyOf(alerts);
  }

  public record Alert(String type, String severity, String summary) {}
}

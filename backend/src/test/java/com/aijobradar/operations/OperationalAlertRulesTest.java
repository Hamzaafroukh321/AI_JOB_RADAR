package com.aijobradar.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationalAlertRulesTest {
  private final OperationalAlertRules rules = new OperationalAlertRules();
  private final OperationsProperties properties = new OperationsProperties(500, 3, 2);

  @Test
  void alertsAtThresholdAndEscalatesAtTwiceThreshold() {
    assertThat(rules.evaluate(2, 1, properties)).isEmpty();
    assertThat(rules.evaluate(3, 2, properties))
        .extracting(OperationalAlertRules.Alert::severity)
        .containsExactly("WARNING", "WARNING");
    assertThat(rules.evaluate(6, 4, properties))
        .extracting(OperationalAlertRules.Alert::severity)
        .containsExactly("CRITICAL", "CRITICAL");
  }
}

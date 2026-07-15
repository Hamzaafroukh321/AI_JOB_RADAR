package com.aijobradar.operations;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OperationalMonitor {
  private final JdbcClient jdbc;
  private final Clock clock;
  private final OperationsProperties properties;
  private final OperationalAlertRules rules;
  private final AtomicLong sourceFailures = new AtomicLong();
  private final AtomicLong aiFailures = new AtomicLong();

  public OperationalMonitor(
      JdbcClient jdbc,
      Clock clock,
      OperationsProperties properties,
      OperationalAlertRules rules,
      MeterRegistry registry) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.properties = properties;
    this.rules = rules;
    Gauge.builder("radar.source.failures.recent", sourceFailures, AtomicLong::get)
        .description("Source failures during the alert window")
        .register(registry);
    Gauge.builder("radar.ai.failures.recent", aiFailures, AtomicLong::get)
        .description("AI failures during the alert window")
        .register(registry);
  }

  @Scheduled(cron = "${radar.operations.monitor-cron:0 */5 * * * *}")
  @Transactional
  public void monitor() {
    OffsetDateTime since = now().minusHours(1);
    long sources =
        count(
            """
            SELECT count(*) FROM fetch_runs
            WHERE created_at>=:since AND status IN ('FAILED_RETRYABLE','FAILED_PERMANENT')
            """,
            since);
    long ai =
        count(
            """
            SELECT count(*) FROM ai_runs
            WHERE created_at>=:since AND status IN ('FAILED_VALIDATION','FAILED_PROVIDER','RATE_LIMITED')
            """,
            since);
    sourceFailures.set(sources);
    aiFailures.set(ai);
    List<OperationalAlertRules.Alert> current = rules.evaluate(sources, ai, properties);
    for (OperationalAlertRules.Alert alert : current) upsert(alert);
    resolveMissing(current.stream().map(OperationalAlertRules.Alert::type).toList());
  }

  @Scheduled(cron = "${radar.operations.retention-cron:0 20 2 * * *}")
  @Transactional
  public void redactExpiredRawPayloads() {
    jdbc.sql(
            """
            UPDATE raw_job_records SET raw_payload='[RETAINED METADATA ONLY]',payload_redacted_at=:now
            WHERE id IN (
              SELECT id FROM raw_job_records
              WHERE retention_delete_at<:now AND payload_redacted_at IS NULL
              ORDER BY retention_delete_at LIMIT :batch
            )
            """)
        .param("now", now())
        .param("batch", properties.retentionBatchSize())
        .update();
  }

  private long count(String sql, OffsetDateTime since) {
    return jdbc.sql(sql).param("since", since).query(Long.class).single();
  }

  private void upsert(OperationalAlertRules.Alert alert) {
    OffsetDateTime now = now();
    jdbc.sql(
            """
            INSERT INTO operational_alerts(id,alert_type,fingerprint,severity,status,summary,
              first_seen_at,last_seen_at,occurrence_count)
            VALUES (:id,:type,:type,:severity,'OPEN',:summary,:now,:now,1)
            ON CONFLICT(fingerprint) DO UPDATE SET severity=EXCLUDED.severity,status='OPEN',
              summary=EXCLUDED.summary,last_seen_at=EXCLUDED.last_seen_at,resolved_at=NULL,
              occurrence_count=operational_alerts.occurrence_count+1
            """)
        .param("id", UUID.randomUUID())
        .param("type", alert.type())
        .param("severity", alert.severity())
        .param("summary", alert.summary())
        .param("now", now)
        .update();
  }

  private void resolveMissing(List<String> active) {
    if (active.isEmpty()) {
      jdbc.sql(
              "UPDATE operational_alerts SET status='RESOLVED',resolved_at=:now WHERE status='OPEN'")
          .param("now", now())
          .update();
      return;
    }
    jdbc.sql(
            """
            UPDATE operational_alerts SET status='RESOLVED',resolved_at=:now
            WHERE status='OPEN' AND alert_type NOT IN (:active)
            """)
        .param("now", now())
        .param("active", active)
        .update();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}

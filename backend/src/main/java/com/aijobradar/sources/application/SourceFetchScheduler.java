package com.aijobradar.sources.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "radar.sources.scheduling-enabled", havingValue = "true")
public class SourceFetchScheduler {
  private final IngestionService ingestion;

  public SourceFetchScheduler(IngestionService ingestion) {
    this.ingestion = ingestion;
  }

  @Scheduled(
      cron = "${radar.sources.cron:0 0 */4 * * *}",
      zone = "${radar.sources.timezone:Africa/Casablanca}")
  public void fetchEnabledSources() {
    ingestion.fetchAllScheduled();
  }
}

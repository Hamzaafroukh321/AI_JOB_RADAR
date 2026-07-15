package com.aijobradar.matching.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class MatchRecomputeQueue {
  private final JdbcClient jdbc;
  private final Clock clock;

  public MatchRecomputeQueue(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  public void profileChanged(UUID userId) {
    jdbc.sql(
            """
            INSERT INTO match_recompute_queue(id,user_id,job_id,reason,requested_at)
            VALUES (:id,:userId,NULL,'PROFILE_CHANGED',:now)
            """)
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("now", now())
        .update();
  }

  public void jobChanged(UUID jobId) {
    jdbc.sql(
            """
            INSERT INTO match_recompute_queue(id,user_id,job_id,reason,requested_at)
            SELECT gen_random_uuid(),id,:jobId,'JOB_CHANGED',:now FROM app_users
            """)
        .param("jobId", jobId)
        .param("now", now())
        .update();
  }

  public void requested(UUID userId, UUID jobId) {
    jdbc.sql(
            """
            INSERT INTO match_recompute_queue(id,user_id,job_id,reason,requested_at)
            VALUES (:id,:userId,:jobId,'USER_REQUESTED',:now)
            """)
        .param("id", UUID.randomUUID())
        .param("userId", userId)
        .param("jobId", jobId)
        .param("now", now())
        .update();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}

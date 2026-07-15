package com.aijobradar.storage.infrastructure;

import com.aijobradar.common.config.RadarProperties;
import com.aijobradar.storage.application.ObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("objectStorage")
public class MinioObjectStorage implements ObjectStorage, HealthIndicator {
  private final MinioClient client;
  private final RadarProperties properties;

  public MinioObjectStorage(MinioClient client, RadarProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public boolean isAvailable() {
    if (!properties.storage().enabled()) return true;
    try {
      return client.bucketExists(
          BucketExistsArgs.builder().bucket(properties.storage().bucket()).build());
    } catch (Exception exception) {
      return false;
    }
  }

  @Override
  public void put(String key, byte[] content, String contentType) {
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(properties.storage().bucket()).object(key).stream(
                  new ByteArrayInputStream(content), (long) content.length, -1L)
              .contentType(contentType)
              .build());
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to store private document", exception);
    }
  }

  @Override
  public byte[] get(String key) {
    try (var stream =
        client.getObject(
            GetObjectArgs.builder().bucket(properties.storage().bucket()).object(key).build())) {
      return stream.readAllBytes();
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read private document", exception);
    }
  }

  @Override
  public Health health() {
    return isAvailable()
        ? Health.up().build()
        : Health.down().withDetail("reason", "Object storage unavailable").build();
  }
}

package com.aijobradar.storage.infrastructure;

import com.aijobradar.common.config.RadarProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {
  @Bean
  MinioClient minioClient(RadarProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.storage().endpoint())
        .credentials(properties.storage().accessKey(), properties.storage().secretKey())
        .build();
  }
}

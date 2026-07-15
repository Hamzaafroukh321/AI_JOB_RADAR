package com.aijobradar.operations;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("radar.operations")
public record OperationsProperties(
    @Min(1) int retentionBatchSize,
    @Min(1) int failedSourceAlertThreshold,
    @Min(1) int failedAiAlertThreshold) {}

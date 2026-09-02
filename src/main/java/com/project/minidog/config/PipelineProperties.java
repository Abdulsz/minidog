package com.project.minidog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minidog.pipeline")
public record PipelineProperties(
        int queueCapacity,
        int workerCount,
        int cardinalityLimit,
        int flushIntervalSeconds) {

    public PipelineProperties {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queue capacity must be positive");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("worker count must be positive");
        }
        if (cardinalityLimit <= 0) {
            throw new IllegalArgumentException("cardinality limit must be positive");
        }
        if (flushIntervalSeconds <= 0) {
            throw new IllegalArgumentException("flush interval must be positive");
        }
    }
}

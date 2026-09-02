package com.project.minidog.api;

public record PipelineStatus(
        int queueDepth,
        int queueCapacity,
        int workerCount,
        int activeSeries,
        long acceptedEvents,
        long backpressureRejectedEvents,
        long aggregatedEvents,
        long cardinalityDroppedEvents,
        long failedEvents,
        long persistedSeries,
        long failedFlushes,
        long lastSuccessfulFlush) {
}

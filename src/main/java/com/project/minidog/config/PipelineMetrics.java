package com.project.minidog.config;

import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.aggregation.CardinalityLimiter;
import com.project.minidog.ingestion.EventQueue;
import com.project.minidog.ingestion.EventWorkers;
import com.project.minidog.persistence.AggregateFlusher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PipelineMetrics {

    public PipelineMetrics(
            MeterRegistry registry,
            EventQueue queue,
            EventWorkers workers,
            Aggregator aggregator,
            CardinalityLimiter cardinalityLimiter,
            AggregateFlusher flusher) {
        registry.gauge("minidog.queue.depth", queue, EventQueue::size);
        registry.gauge("minidog.queue.capacity", queue, EventQueue::capacity);
        registry.gauge("minidog.events.accepted", queue, EventQueue::acceptedEventCount);
        registry.gauge("minidog.events.backpressure_rejected", queue, EventQueue::rejectedEventCount);
        registry.gauge("minidog.events.aggregated", workers, EventWorkers::aggregatedEventCount);
        registry.gauge("minidog.events.cardinality_dropped", workers,
                EventWorkers::cardinalityDroppedEventCount);
        registry.gauge("minidog.events.failed", workers, EventWorkers::failedEventCount);
        registry.gauge("minidog.series.active", aggregator, Aggregator::activeSeriesCount);
        registry.gauge("minidog.cardinality.rejected", cardinalityLimiter,
                CardinalityLimiter::rejectedEventCount);
        registry.gauge("minidog.flush.persisted_series", flusher,
                AggregateFlusher::persistedSeriesCount);
        registry.gauge("minidog.flush.failures", flusher, AggregateFlusher::failedFlushCount);
    }
}

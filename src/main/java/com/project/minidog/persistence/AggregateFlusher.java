package com.project.minidog.persistence;

import tools.jackson.databind.ObjectMapper;
import com.project.minidog.aggregation.AggregateSnapshot;
import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.aggregation.MetricKey;
import com.project.minidog.config.PipelineProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AggregateFlusher {

    private static final Logger log = LoggerFactory.getLogger(AggregateFlusher.class);

    private final Aggregator aggregator;
    private final AggregateRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int intervalSeconds;
    private ScheduledExecutorService scheduler;
    private Map<MetricKey, AggregateSnapshot> pending = Map.of();
    private final LongAdder persistedSeries = new LongAdder();
    private final LongAdder failedFlushes = new LongAdder();
    private volatile long lastSuccessfulFlush;

    public AggregateFlusher(
            Aggregator aggregator,
            AggregateRecordRepository repository,
            ObjectMapper objectMapper,
            Clock clock,
            PipelineProperties properties) {
        this.aggregator = aggregator;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.intervalSeconds = properties.flushIntervalSeconds();
    }

    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("aggregate-flusher")
                .factory());
        scheduler.scheduleWithFixedDelay(
                this::flushSafely, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public synchronized int flushNow() {
        if (pending.isEmpty()) {
            pending = aggregator.drain();
        }
        if (pending.isEmpty()) {
            return 0;
        }

        long bucketTime = clock.instant().getEpochSecond();
        List<AggregateRecord> records = pending.entrySet().stream()
                .map(entry -> new AggregateRecord(
                        entry.getKey(), entry.getValue(), bucketTime, objectMapper))
                .toList();
        repository.saveAll(records);
        pending = Map.of();
        persistedSeries.add(records.size());
        lastSuccessfulFlush = bucketTime;
        return records.size();
    }

    private void flushSafely() {
        try {
            int count = flushNow();
            if (count > 0) {
                log.debug("Persisted {} metric aggregates", count);
            }
        } catch (RuntimeException exception) {
            failedFlushes.increment();
            log.error("Aggregate flush failed; retaining the batch for retry", exception);
        }
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        flushSafely();
    }

    public long persistedSeriesCount() {
        return persistedSeries.sum();
    }

    public long failedFlushCount() {
        return failedFlushes.sum();
    }

    public long lastSuccessfulFlush() {
        return lastSuccessfulFlush;
    }
}

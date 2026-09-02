package com.project.minidog.ingestion;

import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.aggregation.CardinalityLimiter;
import com.project.minidog.config.PipelineProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EventWorkers {

    private static final Logger log = LoggerFactory.getLogger(EventWorkers.class);

    private final EventQueue eventQueue;
    private final Aggregator aggregator;
    private final CardinalityLimiter cardinalityLimiter;
    private final int workerCount;
    private ExecutorService executor;

    public EventWorkers(
            EventQueue eventQueue,
            Aggregator aggregator,
            CardinalityLimiter cardinalityLimiter,
            PipelineProperties properties) {
        this.eventQueue = eventQueue;
        this.aggregator = aggregator;
        this.cardinalityLimiter = cardinalityLimiter;
        this.workerCount = properties.workerCount();
    }

    @PostConstruct
    void start() {
        executor = Executors.newFixedThreadPool(workerCount, Thread.ofPlatform()
                .name("metric-worker-", 0)
                .factory());
        for (int index = 0; index < workerCount; index++) {
            executor.submit(this::processEvents);
        }
    }

    private void processEvents() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var event = eventQueue.take();
                if (cardinalityLimiter.allow(event)) {
                    aggregator.accept(event);
                } else {
                    log.debug("Dropping metric event after cardinality limit: {}", event.name());
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                log.error("Dropping metric event after worker failure", exception);
            }
        }
    }

    @PreDestroy
    void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Metric workers did not stop within five seconds");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

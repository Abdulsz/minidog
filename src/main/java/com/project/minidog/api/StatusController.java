package com.project.minidog.api;

import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.ingestion.EventQueue;
import com.project.minidog.ingestion.EventWorkers;
import com.project.minidog.persistence.AggregateFlusher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/status")
public class StatusController {

    private final EventQueue queue;
    private final EventWorkers workers;
    private final Aggregator aggregator;
    private final AggregateFlusher flusher;

    public StatusController(
            EventQueue queue,
            EventWorkers workers,
            Aggregator aggregator,
            AggregateFlusher flusher) {
        this.queue = queue;
        this.workers = workers;
        this.aggregator = aggregator;
        this.flusher = flusher;
    }

    @GetMapping
    public PipelineStatus status() {
        return new PipelineStatus(
                queue.size(),
                queue.capacity(),
                workers.workerCount(),
                aggregator.activeSeriesCount(),
                queue.acceptedEventCount(),
                queue.rejectedEventCount(),
                workers.aggregatedEventCount(),
                workers.cardinalityDroppedEventCount(),
                workers.failedEventCount(),
                flusher.persistedSeriesCount(),
                flusher.failedFlushCount(),
                flusher.lastSuccessfulFlush());
    }
}

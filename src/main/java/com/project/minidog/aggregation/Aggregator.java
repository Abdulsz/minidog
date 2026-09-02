package com.project.minidog.aggregation;

import com.project.minidog.model.MetricEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class Aggregator {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private ConcurrentHashMap<MetricKey, MetricAccumulator> aggregates = new ConcurrentHashMap<>();

    public void accept(MetricEvent event) {
        lock.readLock().lock();
        try {
            aggregates.computeIfAbsent(MetricKey.from(event), ignored -> new MetricAccumulator(event.type()))
                    .add(event.value());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<MetricKey, AggregateSnapshot> snapshot() {
        lock.readLock().lock();
        try {
            return snapshotOf(aggregates);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<MetricKey, AggregateSnapshot> drain() {
        lock.writeLock().lock();
        try {
            var drained = aggregates;
            aggregates = new ConcurrentHashMap<>();
            return snapshotOf(drained);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Map<MetricKey, AggregateSnapshot> snapshotOf(
            ConcurrentHashMap<MetricKey, MetricAccumulator> source) {
        return source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().snapshot()));
    }
}

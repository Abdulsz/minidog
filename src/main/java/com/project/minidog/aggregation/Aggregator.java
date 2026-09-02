package com.project.minidog.aggregation;

import com.project.minidog.model.MetricEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class Aggregator {

    private final ConcurrentHashMap<MetricKey, MetricAccumulator> aggregates = new ConcurrentHashMap<>();

    public void accept(MetricEvent event) {
        aggregates.computeIfAbsent(MetricKey.from(event), ignored -> new MetricAccumulator(event.type()))
                .add(event.value());
    }

    public Map<MetricKey, AggregateSnapshot> snapshot() {
        return aggregates.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().snapshot()));
    }
}

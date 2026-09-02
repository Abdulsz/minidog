package com.project.minidog.aggregation;

import com.project.minidog.config.PipelineProperties;
import com.project.minidog.model.MetricEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class CardinalityLimiter {

    private final ConcurrentHashMap<String, Set<String>> seenCombinations = new ConcurrentHashMap<>();
    private final LongAdder rejectedEvents = new LongAdder();
    private final int limit;

    public CardinalityLimiter(PipelineProperties properties) {
        this.limit = properties.cardinalityLimit();
    }

    public boolean allow(MetricEvent event) {
        Set<String> combinations = seenCombinations.computeIfAbsent(
                event.name(), ignored -> ConcurrentHashMap.newKeySet());
        String combination = MetricKey.serializeTags(event.tags());

        if (combinations.contains(combination)) {
            return true;
        }

        synchronized (combinations) {
            if (combinations.contains(combination)) {
                return true;
            }
            if (combinations.size() >= limit) {
                rejectedEvents.increment();
                return false;
            }
            combinations.add(combination);
            return true;
        }
    }

    public long rejectedEventCount() {
        return rejectedEvents.sum();
    }

    public int combinationCount(String metricName) {
        Set<String> combinations = seenCombinations.get(metricName);
        return combinations == null ? 0 : combinations.size();
    }
}

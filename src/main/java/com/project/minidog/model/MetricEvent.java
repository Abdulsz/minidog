package com.project.minidog.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record MetricEvent(
        String name,
        double value,
        MetricType type,
        Map<String, String> tags,
        long timestamp) {

    public MetricEvent {
        tags = tags == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(tags));
    }
}

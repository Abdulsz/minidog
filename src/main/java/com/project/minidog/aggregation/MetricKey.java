package com.project.minidog.aggregation;

import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.util.Map;
import java.util.stream.Collectors;

public record MetricKey(String name, MetricType type, String tagCombination) {

    public static MetricKey from(MetricEvent event) {
        return new MetricKey(event.name(), event.type(), serializeTags(event.tags()));
    }

    static String serializeTags(Map<String, String> tags) {
        return tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}

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
                .map(entry -> encode(entry.getKey()) + encode(entry.getValue()))
                .collect(Collectors.joining());
    }

    private static String encode(String value) {
        return value.length() + ":" + value;
    }
}

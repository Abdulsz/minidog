package com.project.minidog.aggregation;

import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.util.Map;
import java.util.TreeMap;
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

    public Map<String, String> tags() {
        Map<String, String> tags = new TreeMap<>();
        int position = 0;
        while (position < tagCombination.length()) {
            Decoded key = decode(tagCombination, position);
            Decoded value = decode(tagCombination, key.nextPosition());
            tags.put(key.value(), value.value());
            position = value.nextPosition();
        }
        return Map.copyOf(tags);
    }

    private static String encode(String value) {
        return value.length() + ":" + value;
    }

    private static Decoded decode(String encoded, int position) {
        int colon = encoded.indexOf(':', position);
        if (colon < 0) {
            throw new IllegalArgumentException("invalid tag encoding");
        }
        int length = Integer.parseInt(encoded.substring(position, colon));
        int valueStart = colon + 1;
        int valueEnd = valueStart + length;
        if (valueEnd > encoded.length()) {
            throw new IllegalArgumentException("invalid tag encoding");
        }
        return new Decoded(encoded.substring(valueStart, valueEnd), valueEnd);
    }

    private record Decoded(String value, int nextPosition) {
    }
}

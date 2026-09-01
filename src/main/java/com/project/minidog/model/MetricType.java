package com.project.minidog.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MetricType {
    GAUGE,
    COUNTER,
    HISTOGRAM;

    @JsonCreator
    public static MetricType fromJson(String value) {
        if (value == null) {
            return null;
        }

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "type must be one of: gauge, counter, histogram", exception);
        }
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}

package com.project.minidog.api;

import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.Map;

public record MetricEventRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "value is required") Double value,
        @NotNull(message = "type is required") MetricType type,
        @Size(max = 20, message = "tags must contain at most 20 entries") Map<String, String> tags,
        @PositiveOrZero(message = "timestamp must be a Unix timestamp") Long timestamp) {

    @AssertTrue(message = "value must be finite")
    public boolean isValueFinite() {
        return value == null || Double.isFinite(value);
    }

    public MetricEvent toEvent(Clock clock) {
        long eventTimestamp = timestamp == null ? clock.instant().getEpochSecond() : timestamp;
        return new MetricEvent(name, value, type, tags, eventTimestamp);
    }
}

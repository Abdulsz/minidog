package com.project.minidog.api;

import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import java.time.Clock;
import java.util.List;
import java.util.Map;

public record IngestRequest(
        String name,
        Double value,
        MetricType type,
        Map<String, String> tags,
        Long timestamp,
        List<@Valid MetricEventRequest> events) {

    @AssertTrue(message = "provide either one complete metric event or a non-empty events batch")
    public boolean isShapeValid() {
        if (events != null) {
            return !events.isEmpty()
                    && name == null
                    && value == null
                    && type == null
                    && tags == null
                    && timestamp == null;
        }
        return name != null && !name.isBlank() && value != null && type != null;
    }

    @AssertTrue(message = "value must be finite")
    public boolean isValueFinite() {
        return events != null || value == null || Double.isFinite(value);
    }

    @AssertTrue(message = "tags must contain at most 20 entries")
    public boolean isTagCountValid() {
        return events != null || tags == null || tags.size() <= 20;
    }

    @AssertTrue(message = "timestamp must be a Unix timestamp")
    public boolean isTimestampValid() {
        return events != null || timestamp == null || timestamp >= 0;
    }

    public List<MetricEvent> toEvents(Clock clock) {
        if (events != null) {
            return events.stream().map(event -> event.toEvent(clock)).toList();
        }
        long eventTimestamp = timestamp == null ? clock.instant().getEpochSecond() : timestamp;
        return List.of(new MetricEvent(name, value, type, tags, eventTimestamp));
    }

    public boolean isBatch() {
        return events != null;
    }
}

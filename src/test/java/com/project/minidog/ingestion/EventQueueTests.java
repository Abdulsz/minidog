package com.project.minidog.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.Test;

class EventQueueTests {

    @Test
    void rejectsEntireBatchWhenCapacityIsInsufficient() {
        EventQueue eventQueue = new EventQueue(new ArrayBlockingQueue<>(2));
        MetricEvent first = event("first");

        assertThat(eventQueue.offer(first)).isTrue();
        assertThat(eventQueue.offerAll(List.of(event("second"), event("third")))).isFalse();
        assertThat(eventQueue.size()).isEqualTo(1);
    }

    private MetricEvent event(String name) {
        return new MetricEvent(name, 1, MetricType.COUNTER, Map.of(), 1);
    }
}

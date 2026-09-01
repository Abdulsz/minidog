package com.project.minidog;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.ingestion.EventQueue;
import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PipelineIntegrationTests {

    @Autowired
    private EventQueue eventQueue;

    @Autowired
    private Aggregator aggregator;

    @Test
    void workerMovesQueuedEventIntoAggregate() throws Exception {
        MetricEvent event = new MetricEvent(
                "pipeline.integration",
                7,
                MetricType.GAUGE,
                Map.of("env", "test"),
                1);

        assertThat(eventQueue.offer(event)).isTrue();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (aggregator.snapshot().isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }

        assertThat(aggregator.snapshot().values())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.count()).isEqualTo(1);
                    assertThat(snapshot.sum()).isEqualTo(7);
                });
    }
}

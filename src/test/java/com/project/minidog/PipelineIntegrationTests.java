package com.project.minidog;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.aggregation.CardinalityLimiter;
import com.project.minidog.aggregation.MetricKey;
import com.project.minidog.ingestion.EventQueue;
import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "minidog.pipeline.cardinality-limit=1")
class PipelineIntegrationTests {

    @Autowired
    private EventQueue eventQueue;

    @Autowired
    private Aggregator aggregator;

    @Autowired
    private CardinalityLimiter cardinalityLimiter;

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

    @Test
    void workerDropsNewCombinationAfterCardinalityLimitButKeepsEstablishedCombination() throws Exception {
        String metricName = "pipeline.cardinality";
        MetricEvent established = new MetricEvent(
                metricName, 2, MetricType.COUNTER, Map.of("service", "checkout"), 1);
        MetricEvent overflow = new MetricEvent(
                metricName, 100, MetricType.COUNTER, Map.of("service", "billing"), 1);

        assertThat(eventQueue.offer(established)).isTrue();
        await(() -> cardinalityLimiter.combinationCount(metricName) == 1
                && aggregator.snapshot().get(MetricKey.from(established)) != null);
        assertThat(eventQueue.offer(overflow)).isTrue();
        assertThat(eventQueue.offer(established)).isTrue();

        await(() -> cardinalityLimiter.rejectedEventCount() == 1
                && aggregator.snapshot().get(MetricKey.from(established)) != null
                && aggregator.snapshot().get(MetricKey.from(established)).count() == 2);

        assertThat(cardinalityLimiter.combinationCount(metricName)).isEqualTo(1);
        assertThat(aggregator.snapshot().get(MetricKey.from(established)).sum()).isEqualTo(4);
        assertThat(aggregator.snapshot()).doesNotContainKey(MetricKey.from(overflow));
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}

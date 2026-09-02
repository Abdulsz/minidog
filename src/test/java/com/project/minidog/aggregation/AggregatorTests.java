package com.project.minidog.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AggregatorTests {

    @Test
    void combinesEquivalentTagSetsRegardlessOfInputOrder() {
        Aggregator aggregator = new Aggregator();

        aggregator.accept(event(2, Map.of("service", "checkout", "env", "prod")));
        aggregator.accept(event(4, Map.of("env", "prod", "service", "checkout")));

        assertThat(aggregator.snapshot()).hasSize(1);
        AggregateSnapshot snapshot = aggregator.snapshot().values().iterator().next();
        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.sum()).isEqualTo(6);
        assertThat(snapshot.average()).isEqualTo(3);
        assertThat(snapshot.latest()).isEqualTo(4);
    }

    @Test
    void aggregatesConcurrentUpdatesWithoutLosingEvents() throws Exception {
        Aggregator aggregator = new Aggregator();
        int eventCount = 2_000;
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < eventCount; index++) {
                executor.submit(() -> aggregator.accept(event(1, Map.of("env", "test"))));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        AggregateSnapshot snapshot = aggregator.snapshot().values().iterator().next();
        assertThat(snapshot.count()).isEqualTo(eventCount);
        assertThat(snapshot.sum()).isEqualTo(eventCount);
    }

    @Test
    void calculatesNearestRankHistogramPercentiles() {
        Aggregator aggregator = new Aggregator();
        for (int value = 1; value <= 100; value++) {
            aggregator.accept(event(value, Map.of("env", "test")));
        }

        AggregateSnapshot snapshot = aggregator.snapshot().values().iterator().next();
        assertThat(snapshot.p50()).isEqualTo(50);
        assertThat(snapshot.p95()).isEqualTo(95);
        assertThat(snapshot.p99()).isEqualTo(99);
    }

    @Test
    void omitsPercentilesForNonHistogramMetrics() {
        Aggregator aggregator = new Aggregator();
        aggregator.accept(new MetricEvent("temperature", 20, MetricType.GAUGE, Map.of(), 1));

        AggregateSnapshot snapshot = aggregator.snapshot().values().iterator().next();
        assertThat(snapshot.p50()).isNull();
        assertThat(snapshot.p95()).isNull();
        assertThat(snapshot.p99()).isNull();
    }

    @Test
    void drainReturnsAWindowAndStartsTheNextOneEmpty() {
        Aggregator aggregator = new Aggregator();
        aggregator.accept(event(5, Map.of("env", "test")));

        Map<MetricKey, AggregateSnapshot> drained = aggregator.drain();

        assertThat(drained).hasSize(1);
        assertThat(drained.values().iterator().next().sum()).isEqualTo(5);
        assertThat(aggregator.snapshot()).isEmpty();
    }

    private MetricEvent event(double value, Map<String, String> tags) {
        return new MetricEvent("api.request.duration", value, MetricType.HISTOGRAM, tags, 1);
    }
}

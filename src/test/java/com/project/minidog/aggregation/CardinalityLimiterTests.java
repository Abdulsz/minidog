package com.project.minidog.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.minidog.config.PipelineProperties;
import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CardinalityLimiterTests {

    @Test
    void rejectsOnlyNewCombinationsAfterLimit() {
        CardinalityLimiter limiter = limiterWithLimit(2);

        assertThat(limiter.allow(event("requests", "checkout"))).isTrue();
        assertThat(limiter.allow(event("requests", "billing"))).isTrue();
        assertThat(limiter.allow(event("requests", "search"))).isFalse();
        assertThat(limiter.allow(event("requests", "checkout"))).isTrue();

        assertThat(limiter.combinationCount("requests")).isEqualTo(2);
        assertThat(limiter.rejectedEventCount()).isEqualTo(1);
    }

    @Test
    void appliesLimitIndependentlyPerMetricName() {
        CardinalityLimiter limiter = limiterWithLimit(1);

        assertThat(limiter.allow(event("requests", "checkout"))).isTrue();
        assertThat(limiter.allow(event("latency", "billing"))).isTrue();

        assertThat(limiter.combinationCount("requests")).isEqualTo(1);
        assertThat(limiter.combinationCount("latency")).isEqualTo(1);
    }

    @Test
    void delimiterCharactersCannotCreateFalseDuplicateCombinations() {
        CardinalityLimiter limiter = limiterWithLimit(1);
        MetricEvent first = new MetricEvent(
                "requests", 1, MetricType.COUNTER, Map.of("a", "b,c=d"), 1);
        MetricEvent second = new MetricEvent(
                "requests", 1, MetricType.COUNTER, Map.of("a", "b", "c", "d"), 1);

        assertThat(limiter.allow(first)).isTrue();
        assertThat(limiter.allow(second)).isFalse();
    }

    @Test
    void neverExceedsLimitUnderConcurrentNewCombinations() throws Exception {
        int limit = 25;
        CardinalityLimiter limiter = limiterWithLimit(limit);
        AtomicInteger accepted = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 500; index++) {
                String service = "service-" + index;
                executor.submit(() -> {
                    if (limiter.allow(event("requests", service))) {
                        accepted.incrementAndGet();
                    }
                });
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(accepted).hasValue(limit);
        assertThat(limiter.combinationCount("requests")).isEqualTo(limit);
        assertThat(limiter.rejectedEventCount()).isEqualTo(500 - limit);
    }

    private CardinalityLimiter limiterWithLimit(int limit) {
        return new CardinalityLimiter(new PipelineProperties(10, 1, limit));
    }

    private MetricEvent event(String name, String service) {
        return new MetricEvent(name, 1, MetricType.COUNTER, Map.of("service", service), 1);
    }
}

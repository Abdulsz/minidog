package com.project.minidog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.minidog.aggregation.Aggregator;
import com.project.minidog.api.QueryResponse;
import com.project.minidog.api.QueryService;
import com.project.minidog.model.MetricEvent;
import com.project.minidog.model.MetricType;
import com.project.minidog.persistence.AggregateFlusher;
import com.project.minidog.persistence.AggregateRecordRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PersistenceIntegrationTests {

    @Autowired
    private Aggregator aggregator;

    @Autowired
    private AggregateFlusher flusher;

    @Autowired
    private AggregateRecordRepository repository;

    @Autowired
    private QueryService queryService;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        aggregator.drain();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void flushesAggregatesAndQueriesThemByTag() {
        String metric = "persistence.latency";
        aggregator.accept(event(metric, 10, "checkout"));
        aggregator.accept(event(metric, 30, "checkout"));
        aggregator.accept(event(metric, 90, "billing"));

        assertThat(flusher.flushNow()).isEqualTo(2);

        QueryResponse response = queryService.query(
                metric, 0, Instant.now().plusSeconds(5).getEpochSecond(),
                Map.of("service", "checkout"), "average");
        assertThat(response.points()).singleElement().satisfies(point -> {
            assertThat(point.count()).isEqualTo(2);
            assertThat(point.value()).isEqualTo(20);
            assertThat(point.tags()).containsEntry("service", "checkout");
        });
    }

    @Test
    void exposesStoredPointsThroughQueryEndpoint() throws Exception {
        String metric = "query.counter";
        aggregator.accept(new MetricEvent(
                metric, 3, MetricType.COUNTER, Map.of("env", "test"), 1));
        flusher.flushNow();

        mockMvc.perform(get("/query")
                        .param("name", metric)
                        .param("from", "0")
                        .param("to", Long.toString(Instant.now().plusSeconds(5).getEpochSecond()))
                        .param("tag", "env:test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value(metric))
                .andExpect(jsonPath("$.statistic").value("auto"))
                .andExpect(jsonPath("$.points[0].value").value(3));
    }

    @Test
    void rejectsMalformedQueryRanges() throws Exception {
        mockMvc.perform(get("/query")
                        .param("name", "requests")
                        .param("from", "20")
                        .param("to", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void exposesPipelineStatusAndHealth() throws Exception {
        mockMvc.perform(get("/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueCapacity").value(10000))
                .andExpect(jsonPath("$.workerCount").value(2))
                .andExpect(jsonPath("$.acceptedEvents").isNumber());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private MetricEvent event(String name, double value, String service) {
        return new MetricEvent(
                name, value, MetricType.HISTOGRAM, Map.of("service", service), 1);
    }
}

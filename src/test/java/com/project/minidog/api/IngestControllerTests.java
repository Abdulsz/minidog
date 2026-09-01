package com.project.minidog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.minidog.ingestion.EventQueue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IngestControllerTests {

    private EventQueue eventQueue;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eventQueue = new EventQueue(new ArrayBlockingQueue<>(2));
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_723_999_200L), ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new IngestController(eventQueue, clock))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void acceptsSingleEvent() throws Exception {
        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "api.request.duration",
                                  "value": 45.3,
                                  "type": "histogram",
                                  "tags": {"service": "checkout"}
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));
    }

    @Test
    void acceptsBatchWithoutAQueryParameter() throws Exception {
        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"events": [
                                  {"name": "requests", "value": 1, "type": "counter"},
                                  {"name": "requests", "value": 1, "type": "counter"}
                                ]}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2));
    }

    @Test
    void rejectsBatchAtomicallyWhenQueueIsFull() throws Exception {
        eventQueue.offer(new com.project.minidog.model.MetricEvent(
                "existing", 1, com.project.minidog.model.MetricType.COUNTER, java.util.Map.of(), 1));

        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"events": [
                                  {"name": "requests", "value": 1, "type": "counter"},
                                  {"name": "requests", "value": 1, "type": "counter"}
                                ]}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.accepted").value(0));
    }

    @Test
    void rejectsInvalidMetricType() throws Exception {
        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "requests", "value": 1, "type": "timer"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "requests", "type": "counter"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").exists());
    }
}

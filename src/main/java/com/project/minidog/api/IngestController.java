package com.project.minidog.api;

import com.project.minidog.ingestion.EventQueue;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private final EventQueue eventQueue;
    private final Clock clock;

    public IngestController(EventQueue eventQueue, Clock clock) {
        this.eventQueue = eventQueue;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        var events = request.toEvents(clock);
        boolean accepted = request.isBatch()
                ? eventQueue.offerAll(events)
                : eventQueue.offer(events.getFirst());
        return response(accepted, accepted ? events.size() : 0);
    }

    private ResponseEntity<IngestResponse> response(boolean accepted, int count) {
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new IngestResponse("queue_full", 0));
        }
        return ResponseEntity.accepted().body(new IngestResponse("accepted", count));
    }
}

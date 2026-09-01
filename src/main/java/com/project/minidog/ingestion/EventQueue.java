package com.project.minidog.ingestion;

import com.project.minidog.model.MetricEvent;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import org.springframework.stereotype.Component;

@Component
public class EventQueue {

    private final BlockingQueue<MetricEvent> queue;

    public EventQueue(BlockingQueue<MetricEvent> queue) {
        this.queue = queue;
    }

    public synchronized boolean offer(MetricEvent event) {
        return queue.offer(event);
    }

    public synchronized boolean offerAll(Collection<MetricEvent> events) {
        if (events.size() > queue.remainingCapacity()) {
            return false;
        }
        return queue.addAll(events);
    }

    public MetricEvent take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}

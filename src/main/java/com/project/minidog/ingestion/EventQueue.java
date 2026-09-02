package com.project.minidog.ingestion;

import com.project.minidog.model.MetricEvent;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class EventQueue {

    private final BlockingQueue<MetricEvent> queue;
    private final LongAdder acceptedEvents = new LongAdder();
    private final LongAdder rejectedEvents = new LongAdder();

    public EventQueue(BlockingQueue<MetricEvent> queue) {
        this.queue = queue;
    }

    public synchronized boolean offer(MetricEvent event) {
        boolean accepted = queue.offer(event);
        recordResult(accepted, 1);
        return accepted;
    }

    public synchronized boolean offerAll(Collection<MetricEvent> events) {
        if (events.size() > queue.remainingCapacity()) {
            rejectedEvents.add(events.size());
            return false;
        }
        boolean accepted = queue.addAll(events);
        recordResult(accepted, events.size());
        return accepted;
    }

    public MetricEvent take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return queue.size() + queue.remainingCapacity();
    }

    public long acceptedEventCount() {
        return acceptedEvents.sum();
    }

    public long rejectedEventCount() {
        return rejectedEvents.sum();
    }

    private void recordResult(boolean accepted, int count) {
        (accepted ? acceptedEvents : rejectedEvents).add(count);
    }
}

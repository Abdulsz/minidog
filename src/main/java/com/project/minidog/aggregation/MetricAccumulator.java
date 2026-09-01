package com.project.minidog.aggregation;

final class MetricAccumulator {

    private long count;
    private double sum;
    private double latest;

    synchronized void add(double value) {
        count++;
        sum += value;
        latest = value;
    }

    synchronized AggregateSnapshot snapshot() {
        return new AggregateSnapshot(count, sum, latest);
    }
}

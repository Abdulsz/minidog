package com.project.minidog.aggregation;

public record AggregateSnapshot(
        long count,
        double sum,
        double latest,
        Double p50,
        Double p95,
        Double p99) {

    public double average() {
        return count == 0 ? 0 : sum / count;
    }
}

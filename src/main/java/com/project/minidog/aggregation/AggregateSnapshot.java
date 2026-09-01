package com.project.minidog.aggregation;

public record AggregateSnapshot(long count, double sum, double latest) {

    public double average() {
        return count == 0 ? 0 : sum / count;
    }
}

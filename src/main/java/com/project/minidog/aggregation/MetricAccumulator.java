package com.project.minidog.aggregation;

import com.project.minidog.model.MetricType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class MetricAccumulator {

    private final MetricType type;
    private final List<Double> histogramValues;
    private long count;
    private double sum;
    private double latest;

    MetricAccumulator(MetricType type) {
        this.type = type;
        this.histogramValues = type == MetricType.HISTOGRAM ? new ArrayList<>() : null;
    }

    synchronized void add(double value) {
        count++;
        sum += value;
        latest = value;
        if (histogramValues != null) {
            histogramValues.add(value);
        }
    }

    synchronized AggregateSnapshot snapshot() {
        if (histogramValues == null || histogramValues.isEmpty()) {
            return new AggregateSnapshot(count, sum, latest, null, null, null);
        }

        List<Double> sorted = histogramValues.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        return new AggregateSnapshot(
                count,
                sum,
                latest,
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99));
    }

    private double percentile(List<Double> sortedValues, double percentile) {
        int nearestRank = (int) Math.ceil(percentile * sortedValues.size());
        int index = Math.max(0, nearestRank - 1);
        return sortedValues.get(index);
    }
}

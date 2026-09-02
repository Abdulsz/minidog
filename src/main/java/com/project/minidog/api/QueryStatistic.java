package com.project.minidog.api;

import com.project.minidog.model.MetricType;
import com.project.minidog.persistence.AggregateRecord;

enum QueryStatistic {
    AUTO,
    SUM,
    AVERAGE,
    LATEST,
    P50,
    P95,
    P99;

    static QueryStatistic parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "stat must be one of: auto, sum, average, latest, p50, p95, p99");
        }
    }

    double valueOf(AggregateRecord record) {
        QueryStatistic resolved = this == AUTO ? defaultFor(record.getMetricType()) : this;
        return switch (resolved) {
            case SUM -> record.getValueSum();
            case AVERAGE -> record.getSampleCount() == 0
                    ? 0 : record.getValueSum() / record.getSampleCount();
            case LATEST -> record.getLatestValue();
            case P50 -> percentile(record.getP50(), "p50");
            case P95 -> percentile(record.getP95(), "p95");
            case P99 -> percentile(record.getP99(), "p99");
            case AUTO -> throw new IllegalStateException("auto statistic was not resolved");
        };
    }

    private QueryStatistic defaultFor(MetricType type) {
        return switch (type) {
            case COUNTER -> SUM;
            case GAUGE -> LATEST;
            case HISTOGRAM -> P95;
        };
    }

    private double percentile(Double value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is only available for histogram metrics");
        }
        return value;
    }

    String wireName() {
        return name().toLowerCase();
    }
}

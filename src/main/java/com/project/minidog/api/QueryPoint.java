package com.project.minidog.api;

import java.util.Map;

public record QueryPoint(
        long time,
        double value,
        long count,
        double sum,
        double latest,
        Double p50,
        Double p95,
        Double p99,
        Map<String, String> tags) {
}

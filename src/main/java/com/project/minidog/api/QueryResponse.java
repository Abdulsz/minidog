package com.project.minidog.api;

import java.util.List;

public record QueryResponse(String metric, String statistic, List<QueryPoint> points) {
}

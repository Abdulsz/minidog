package com.project.minidog.api;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.project.minidog.persistence.AggregateRecord;
import com.project.minidog.persistence.AggregateRecordRepository;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import org.springframework.stereotype.Service;

@Service
public class QueryService {

    private static final TypeReference<Map<String, String>> TAG_MAP = new TypeReference<>() {
    };

    private final AggregateRecordRepository repository;
    private final ObjectMapper objectMapper;

    public QueryService(AggregateRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public QueryResponse query(
            String name,
            long from,
            long to,
            Map<String, String> requiredTags,
            String statisticName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (from < 0 || to < 0 || from > to) {
            throw new IllegalArgumentException("from and to must be valid Unix timestamps");
        }

        QueryStatistic statistic = QueryStatistic.parse(statisticName);
        List<QueryPoint> points = repository
                .findByMetricNameAndBucketTimeBetweenOrderByBucketTimeAsc(name, from, to)
                .stream()
                .map(record -> point(record, statistic))
                .filter(point -> point.tags().entrySet().containsAll(requiredTags.entrySet()))
                .toList();
        return new QueryResponse(name, statistic.wireName(), points);
    }

    private QueryPoint point(AggregateRecord record, QueryStatistic statistic) {
        Map<String, String> tags = readTags(record.getTagsJson());
        return new QueryPoint(
                record.getBucketTime(),
                statistic.valueOf(record),
                record.getSampleCount(),
                record.getValueSum(),
                record.getLatestValue(),
                record.getP50(),
                record.getP95(),
                record.getP99(),
                tags);
    }

    private Map<String, String> readTags(String json) {
        try {
            return objectMapper.readValue(json, TAG_MAP);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored metric tags are unreadable", exception);
        }
    }
}

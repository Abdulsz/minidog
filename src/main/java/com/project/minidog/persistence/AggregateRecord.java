package com.project.minidog.persistence;

import com.project.minidog.aggregation.AggregateSnapshot;
import com.project.minidog.aggregation.MetricKey;
import com.project.minidog.model.MetricType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Map;

@Entity
@Table(name = "metric_aggregates", indexes = {
        @Index(name = "idx_metric_aggregates_name_time", columnList = "metric_name,bucket_time")
})
public class AggregateRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_name", nullable = false, length = 255)
    private String metricName;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 20)
    private MetricType metricType;

    @Column(name = "tag_combination", nullable = false, length = 4000)
    private String tagCombination;

    @Column(name = "tags_json", nullable = false, length = 4000)
    private String tagsJson;

    @Column(name = "bucket_time", nullable = false)
    private long bucketTime;

    @Column(name = "sample_count", nullable = false)
    private long sampleCount;

    @Column(name = "value_sum", nullable = false)
    private double valueSum;

    @Column(name = "latest_value", nullable = false)
    private double latestValue;

    private Double p50;
    private Double p95;
    private Double p99;

    protected AggregateRecord() {
    }

    public AggregateRecord(
            MetricKey key,
            AggregateSnapshot snapshot,
            long bucketTime,
            ObjectMapper objectMapper) {
        this.metricName = key.name();
        this.metricType = key.type();
        this.tagCombination = key.tagCombination();
        this.tagsJson = writeTags(objectMapper, key.tags());
        this.bucketTime = bucketTime;
        this.sampleCount = snapshot.count();
        this.valueSum = snapshot.sum();
        this.latestValue = snapshot.latest();
        this.p50 = snapshot.p50();
        this.p95 = snapshot.p95();
        this.p99 = snapshot.p99();
    }

    private String writeTags(ObjectMapper objectMapper, Map<String, String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("unable to serialize metric tags", exception);
        }
    }

    public Long getId() {
        return id;
    }

    public String getMetricName() {
        return metricName;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public String getTagCombination() {
        return tagCombination;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public long getBucketTime() {
        return bucketTime;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public double getValueSum() {
        return valueSum;
    }

    public double getLatestValue() {
        return latestValue;
    }

    public Double getP50() {
        return p50;
    }

    public Double getP95() {
        return p95;
    }

    public Double getP99() {
        return p99;
    }
}

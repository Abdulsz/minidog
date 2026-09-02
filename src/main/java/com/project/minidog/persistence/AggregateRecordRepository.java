package com.project.minidog.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregateRecordRepository extends JpaRepository<AggregateRecord, Long> {

    List<AggregateRecord> findByMetricNameAndBucketTimeBetweenOrderByBucketTimeAsc(
            String metricName, long from, long to);
}

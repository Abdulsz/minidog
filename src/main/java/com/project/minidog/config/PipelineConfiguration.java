package com.project.minidog.config;

import com.project.minidog.model.MetricEvent;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
public class PipelineConfiguration {

    @Bean
    BlockingQueue<MetricEvent> metricEventBuffer(PipelineProperties properties) {
        return new ArrayBlockingQueue<>(properties.queueCapacity());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

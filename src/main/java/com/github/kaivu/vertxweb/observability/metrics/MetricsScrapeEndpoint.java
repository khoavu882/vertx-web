package com.github.kaivu.vertxweb.observability.metrics;

public interface MetricsScrapeEndpoint {
    String scrape();

    String contentType();
}

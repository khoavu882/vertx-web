package com.github.kaivu.vertxweb.web.rests;

import com.github.kaivu.vertxweb.config.ApplicationConfig;
import com.github.kaivu.vertxweb.constants.PathConstants;
import com.github.kaivu.vertxweb.observability.metrics.MetricsScrapeEndpoint;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MetricsRouter {
    private static final Logger log = LoggerFactory.getLogger(MetricsRouter.class);

    private final ApplicationConfig appConfig;
    private final MetricsScrapeEndpoint metricsScrapeEndpoint;

    @Getter
    private final Router router;

    @Inject
    public MetricsRouter(Vertx vertx, ApplicationConfig appConfig, MetricsScrapeEndpoint metricsScrapeEndpoint) {
        this.appConfig = appConfig;
        this.metricsScrapeEndpoint = metricsScrapeEndpoint;
        this.router = Router.router(vertx);
        initializeRoutes();
    }

    private void initializeRoutes() {
        if (!appConfig.observability().metrics().enable()) {
            log.info("Metrics endpoint is disabled by configuration.");
            return;
        }

        String path = normalizePath(appConfig.observability().metrics().endpointPath());
        router.get(path).handler(this::scrapeMetrics);
    }

    private void scrapeMetrics(RoutingContext ctx) {
        ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", metricsScrapeEndpoint.contentType())
                .end(metricsScrapeEndpoint.scrape());
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return PathConstants.METRICS_ROOT;
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}

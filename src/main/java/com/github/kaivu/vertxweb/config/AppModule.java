package com.github.kaivu.vertxweb.config;

import com.github.kaivu.vertxweb.consumers.AnalyticsConsumer;
import com.github.kaivu.vertxweb.consumers.BatchOperationConsumer;
import com.github.kaivu.vertxweb.consumers.HealthCheckConsumer;
import com.github.kaivu.vertxweb.consumers.LegacyOperationConsumer;
import com.github.kaivu.vertxweb.middlewares.AuthHandler;
import com.github.kaivu.vertxweb.middlewares.ErrorHandler;
import com.github.kaivu.vertxweb.middlewares.LoggingHandler;
import com.github.kaivu.vertxweb.observability.health.DatabaseReadinessCheck;
import com.github.kaivu.vertxweb.observability.health.DefaultHealthCheckRegistry;
import com.github.kaivu.vertxweb.observability.health.DefaultProbeOrchestrator;
import com.github.kaivu.vertxweb.observability.health.EventBusReadinessCheck;
import com.github.kaivu.vertxweb.observability.health.HealthCheck;
import com.github.kaivu.vertxweb.observability.health.HealthCheckRegistry;
import com.github.kaivu.vertxweb.observability.health.ProbeOrchestrator;
import com.github.kaivu.vertxweb.observability.health.ProcessLivenessCheck;
import com.github.kaivu.vertxweb.observability.health.StartupCheck;
import com.github.kaivu.vertxweb.observability.metrics.MetricsFacade;
import com.github.kaivu.vertxweb.observability.metrics.MetricsScrapeEndpoint;
import com.github.kaivu.vertxweb.observability.metrics.MicrometerMetricsFacade;
import com.github.kaivu.vertxweb.observability.metrics.NoopMetricsFacade;
import com.github.kaivu.vertxweb.observability.tracing.TracingService;
import com.github.kaivu.vertxweb.patterns.CircuitBreakerRegistry;
import com.github.kaivu.vertxweb.repositories.ProductRepository;
import com.github.kaivu.vertxweb.repositories.ProductRepositoryImpl;
import com.github.kaivu.vertxweb.repositories.TransactionTemplate;
import com.github.kaivu.vertxweb.services.ProductService;
import com.github.kaivu.vertxweb.services.UserService;
import com.github.kaivu.vertxweb.web.AppRouter;
import com.github.kaivu.vertxweb.web.RouterHelper;
import com.github.kaivu.vertxweb.web.rests.CommonRouter;
import com.github.kaivu.vertxweb.web.rests.HealthRouter;
import com.github.kaivu.vertxweb.web.rests.MetricsRouter;
import com.github.kaivu.vertxweb.web.rests.OpenApiRouter;
import com.github.kaivu.vertxweb.web.rests.ProductRouter;
import com.github.kaivu.vertxweb.web.rests.UserRouter;
import com.github.kaivu.vertxweb.web.routes.RouterConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;

/**
 * Google Guice module for dependency injection configuration.
 *
 * <p>This module follows Guice best practices:
 * - Uses @Provides for complex object creation
 * - Leverages constructor injection where possible via @Inject annotations
 * - Properly scopes singletons for stateful components
 * - Separates configuration binding from service binding
 *
 * <p>Domain routers are registered via {@link Multibinder} so that {@link RouterConfig} receives
 * a {@code Set<AppRouter>} and mounts them automatically. Adding a new domain requires only one
 * {@code addBinding()} line here — RouterConfig itself never needs to change.
 *
 * <p>Health checks follow the same Multibinder pattern — adding a new check requires only one
 * {@code addBinding()} line; {@link DefaultHealthCheckRegistry} never changes.
 */
public class AppModule extends AbstractModule {
    private final Vertx vertx;
    private final ApplicationConfig applicationConfig;

    /**
     * Accepts both Vertx and a pre-loaded ApplicationConfig.
     * The config is parsed once in StartupApp and passed here — no re-parsing per verticle.
     */
    public AppModule(Vertx vertx, ApplicationConfig applicationConfig) {
        this.vertx = vertx;
        this.applicationConfig = applicationConfig;
    }

    @Override
    protected void configure() {
        // Bind external instances that cannot be created by Guice
        bind(Vertx.class).toInstance(vertx);
        bind(ApplicationConfig.class).toInstance(applicationConfig);

        // Bind repositories
        bind(ProductRepository.class).to(ProductRepositoryImpl.class).in(Singleton.class);
        bind(TransactionTemplate.class).in(Singleton.class);

        // Bind services
        bind(UserService.class).in(Singleton.class);
        bind(ProductService.class).in(Singleton.class);

        // Bind middleware handlers
        bind(AuthHandler.class).in(Singleton.class);
        bind(LoggingHandler.class).in(Singleton.class);
        bind(ErrorHandler.class).in(Singleton.class);

        // Bind utility helpers
        bind(RouterHelper.class).in(Singleton.class);

        // Bind patterns and infrastructure
        bind(CircuitBreakerRegistry.class).in(Singleton.class);
        bind(ProbeOrchestrator.class).to(DefaultProbeOrchestrator.class).in(Singleton.class);
        bind(HealthCheckRegistry.class).to(DefaultHealthCheckRegistry.class).in(Singleton.class);
        bind(TracingService.class).in(Singleton.class);

        // Bind EventBus consumers
        bind(AnalyticsConsumer.class).in(Singleton.class);
        bind(BatchOperationConsumer.class).in(Singleton.class);
        bind(HealthCheckConsumer.class).in(Singleton.class);
        bind(LegacyOperationConsumer.class).in(Singleton.class);

        // Bind infrastructure routers individually (special mounting rules)
        bind(CommonRouter.class).in(Singleton.class);
        bind(HealthRouter.class).in(Singleton.class);
        bind(MetricsRouter.class).in(Singleton.class);
        bind(OpenApiRouter.class).in(Singleton.class);

        // Register domain routers via Multibinder.
        // RouterConfig receives Set<AppRouter> and mounts each automatically.
        // To add a new domain: add one line here — nothing else changes.
        Multibinder<AppRouter> domainRouters = Multibinder.newSetBinder(binder(), AppRouter.class);
        domainRouters.addBinding().to(UserRouter.class).in(Singleton.class);
        domainRouters.addBinding().to(ProductRouter.class).in(Singleton.class);

        // Register health checks via Multibinder.
        // DefaultHealthCheckRegistry receives Set<HealthCheck> automatically.
        // To add a new check: add one line here — nothing else changes.
        Multibinder<HealthCheck> healthChecks = Multibinder.newSetBinder(binder(), HealthCheck.class);
        healthChecks.addBinding().to(ProcessLivenessCheck.class).in(Singleton.class);
        healthChecks.addBinding().to(EventBusReadinessCheck.class).in(Singleton.class);
        healthChecks.addBinding().to(StartupCheck.class).in(Singleton.class);
        healthChecks.addBinding().to(DatabaseReadinessCheck.class).in(Singleton.class);

        // Bind router configuration
        bind(RouterConfig.class).in(Singleton.class);
    }

    /**
     * Provides the main Vert.x Router instance.
     * This needs a provider method because Router.router() is a factory method.
     */
    @Provides
    @Singleton
    Router provideMainRouter(Vertx vertx) {
        return Router.router(vertx);
    }

    /**
     * Provides the reactive MariaDB connection pool.
     *
     * <p>This pool is event-loop safe — all queries run asynchronously without blocking.
     * Close it in {@code StartupApp.shutdown()} after all verticles have undeployed.
     *
     * <p>Pool sizing notes:
     * - {@code maxSize}: 5 connections is sufficient for K8s single-pod reactive workloads
     * - {@code maxWaitQueueSize}: bounded to prevent memory growth under burst traffic
     * - {@code connectionTimeoutMs}: short (3 s) so circuit breaker trips before a request hangs
     * - {@code idleTimeoutMs}: 5 min, below typical cloud DB idle-kill interval (8 min)
     */
    @Provides
    @Singleton
    MySQLPool provideMySQLPool(Vertx vertx, ApplicationConfig config) {
        ApplicationConfig.DatabaseConfig db = config.database();
        ApplicationConfig.DatabaseConfig.PoolConfig pool = db.pool();

        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setHost(db.host())
                .setPort(db.port())
                .setDatabase(db.database())
                .setUser(db.username())
                .setPassword(db.password().orElse(""));

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(pool.maxSize())
                .setMaxWaitQueueSize(pool.maxWaitQueueSize())
                .setConnectionTimeout(pool.connectionTimeoutMs())
                .setConnectionTimeoutUnit(TimeUnit.MILLISECONDS)
                .setIdleTimeout((int) pool.idleTimeoutMs())
                .setIdleTimeoutUnit(TimeUnit.MILLISECONDS);

        return MySQLPool.pool(vertx, connectOptions, poolOptions);
    }

    /**
     * Provides a configured Flyway instance for schema migrations.
     *
     * <p>The Flyway JDBC connection is intentionally separate from the reactive pool —
     * Flyway requires blocking JDBC and runs once at startup before the event loop starts.
     * The MariaDB JDBC driver ({@code runtimeOnly} dependency) is used exclusively here.
     */
    @Provides
    @Singleton
    Flyway provideFlyway(ApplicationConfig config) {
        ApplicationConfig.DatabaseConfig db = config.database();
        String url = "jdbc:mariadb://" + db.host() + ":" + db.port() + "/" + db.database();
        return Flyway.configure()
                .dataSource(url, db.username(), db.password().orElse(""))
                .locations("classpath:db/migration")
                .loggers("slf4j")
                .cleanDisabled(true)
                .load();
    }

    @Provides
    @Singleton
    MetricsFacade provideMetricsFacade(ApplicationConfig applicationConfig) {
        if (!applicationConfig.observability().metrics().enable()) {
            return new NoopMetricsFacade();
        }
        String backend = applicationConfig.observability().metrics().backend();
        if ("micrometer".equalsIgnoreCase(backend)) {
            return new MicrometerMetricsFacade(applicationConfig);
        }
        return new NoopMetricsFacade();
    }

    @Provides
    @Singleton
    MetricsScrapeEndpoint provideMetricsScrapeEndpoint(MetricsFacade metricsFacade) {
        if (metricsFacade instanceof MetricsScrapeEndpoint scrapeEndpoint) {
            return scrapeEndpoint;
        }
        return new NoopMetricsFacade();
    }
}

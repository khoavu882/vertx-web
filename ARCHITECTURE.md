# Architecture Reference — vertx-web

High-performance reactive web application template built on Vert.x 4, Mutiny, and Google Guice.
Designed for **Kubernetes-native single-instance deployment** — horizontal scaling is handled externally at the pod level.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Package Structure](#package-structure)
3. [Deployment Model](#deployment-model)
4. [Startup Sequence](#startup-sequence)
5. [Dependency Injection](#dependency-injection)
6. [HTTP Layer](#http-layer)
7. [Service Layer](#service-layer)
8. [EventBus & Worker Layer](#eventbus--worker-layer)
9. [Context & Correlation Tracking](#context--correlation-tracking)
10. [Circuit Breaker](#circuit-breaker)
11. [Observability Stack](#observability-stack)
12. [Configuration Model](#configuration-model)
13. [Validation Framework](#validation-framework)
14. [Technology Stack](#technology-stack)
15. [Extending the Template](#extending-the-template)

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        JVM Process (1 per Pod)                   │
│                                                                   │
│  StartupApp                                                       │
│    │                                                             │
│    ├── AppVerticle  (event-loop thread)                          │
│    │     └── HTTP Server :8081                                   │
│    │           └── RouterConfig (middleware pipeline)            │
│    │                 ├── TimeoutHandler                          │
│    │                 ├── CorsHandler                             │
│    │                 ├── LoggingHandler                          │
│    │                 ├── AuthHandler                             │
│    │                 ├── UserRouter    /api/users/*              │
│    │                 ├── ProductRouter /api/products/*           │
│    │                 ├── CommonRouter  /api/common/*             │
│    │                 ├── HealthRouter  /health/*                 │
│    │                 ├── MetricsRouter /metrics                  │
│    │                 ├── OpenApiRouter /openapi/*                │
│    │                 └── ErrorHandler (failure handler)          │
│    │                                                             │
│    └── WorkerVerticle  (worker-pool thread)                      │
│          └── EventBus Consumers                                  │
│                ├── AnalyticsConsumer                             │
│                ├── BatchOperationConsumer                        │
│                ├── HealthCheckConsumer                           │
│                └── LegacyOperationConsumer                       │
│                                                                   │
│  Shared (per-verticle Guice injector):                           │
│    UserService, ProductService, CircuitBreakerRegistry,          │
│    TracingService, MetricsFacade, HealthCheckRegistry            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.github.kaivu.vertxweb/
├── StartupApp.java                  # Entry point, Vertx bootstrap
├── VertxWrapper.java                # Base reactive wrapper
│
├── config/
│   ├── ApplicationConfig.java       # @ConfigMapping interface (SmallRye)
│   ├── AppModule.java               # Guice DI module
│   └── ConfigProvider.java         # SmallRye config bootstrap
│
├── verticles/
│   ├── AppVerticle.java             # HTTP server verticle (event-loop)
│   └── WorkerVerticle.java          # Background worker verticle
│
├── web/
│   ├── RouterHelper.java            # Static helpers: handleAsync(), sendJsonResponse()
│   ├── routes/
│   │   └── RouterConfig.java        # Central middleware + sub-router wiring
│   ├── rests/
│   │   ├── UserRouter.java
│   │   ├── ProductRouter.java
│   │   ├── CommonRouter.java
│   │   ├── HealthRouter.java
│   │   ├── MetricsRouter.java
│   │   └── OpenApiRouter.java
│   ├── validation/
│   │   ├── Validator.java           # Static validator instances per entity
│   │   ├── ValidationRule.java      # Composable rule interface
│   │   └── ValidationResult.java
│   └── exceptions/
│       └── ServiceException.java    # Carries HTTP status code
│
├── services/
│   ├── UserService.java             # Stub CRUD with circuit breaker
│   └── ProductService.java         # Delegates to ProductRepository
│
├── repositories/
│   ├── ProductRepository.java       # Interface
│   └── ProductRepositoryImpl.java   # Stub in-memory implementation
│
├── middlewares/
│   ├── AuthHandler.java             # Demo Bearer-presence auth
│   ├── LoggingHandler.java          # Request/response logging with MDC
│   └── ErrorHandler.java           # Centralised failure → JSON response
│
├── consumers/
│   ├── EventBusConsumer.java        # Interface
│   ├── AnalyticsConsumer.java
│   ├── BatchOperationConsumer.java
│   ├── HealthCheckConsumer.java
│   └── LegacyOperationConsumer.java
│
├── context/
│   ├── ContextAwareVertxWrapper.java  # Correlation-context-aware wrapper
│   └── CorrelationContext.java        # Request ID, trace/span IDs, MDC
│
├── patterns/
│   ├── CircuitBreaker.java           # Custom FSM circuit breaker
│   ├── CircuitBreakerConfig.java     # record with thresholds/timeouts
│   ├── CircuitBreakerRegistry.java   # Named circuit breaker factory
│   ├── CircuitBreakerObserver.java   # Transition observer interface
│   ├── NoopCircuitBreakerObserver.java
│   └── CircuitBreakerMetrics.java    # Snapshot value object
│
├── observability/
│   ├── metrics/
│   │   ├── MetricsFacade.java        # Interface
│   │   ├── MicrometerMetricsFacade.java
│   │   ├── MetricsScrapeEndpoint.java # Interface extending MetricsFacade
│   │   └── NoopMetricsFacade.java
│   ├── health/
│   │   ├── HealthCheck.java          # Interface
│   │   ├── CheckType.java            # LIVENESS / READINESS / STARTUP
│   │   ├── CheckStatus.java          # UP / DOWN / UNKNOWN
│   │   ├── CheckResult.java          # name + status + details
│   │   ├── HealthPayload.java        # Full health response model
│   │   ├── HealthCheckRegistry.java  # Interface
│   │   ├── DefaultHealthCheckRegistry.java
│   │   ├── ProbeOrchestrator.java    # Interface
│   │   ├── DefaultProbeOrchestrator.java
│   │   ├── NoopProbeOrchestrator.java
│   │   ├── ApplicationState.java     # STARTING / RUNNING / STOPPING
│   │   ├── ProcessLivenessCheck.java
│   │   ├── EventBusReadinessCheck.java
│   │   └── StartupCheck.java
│   └── tracing/
│       └── TracingService.java       # OTel SDK: spans, W3C propagation
│
└── constants/
    ├── HttpStatusCodes.java
    ├── HttpConstants.java
    ├── MessageConstants.java
    ├── PathConstants.java
    ├── EventBusConstants.java
    ├── ContextKeys.java
    ├── AuthConstants.java
    ├── HealthConstants.java
    ├── JsonKeys.java
    ├── OpenApiConstants.java
    ├── OutcomeConstants.java
    └── ProductConstants.java
```

---

## Deployment Model

```
Kubernetes Cluster
  └── Deployment: vertx-web
        └── Pod (replica 1)          <- K8s scales pods
              └── JVM
                    ├── AppVerticle  (1 instance, event-loop)
                    └── WorkerVerticle (1 instance, worker-pool)
        └── Pod (replica 2)
              └── JVM
                    ├── AppVerticle
                    └── WorkerVerticle
        └── Pod (replica N) ...
```

**Key principle:** Vert.x manages internal concurrency (event-loop threads = available CPUs); Kubernetes manages horizontal scale. Do **not** configure multiple `AppVerticle` instances per JVM — this was deliberately removed to avoid circuit-breaker state inconsistency across in-process instances.

The `app.deployment.*` config only tunes **thread pool sizes**, not instance counts.

---

## Startup Sequence

```
StartupApp.main()
  1. ConfigProvider.createConfig()          → cachedConfig (parsed once)
  2. Runtime.addShutdownHook(shutdown-hook)
  3. Vertx.vertx(VertxOptions)             → vertxOptions tuned from cachedConfig
  4. vertx.deployVerticle(AppVerticle, instances=1)
       └── AppVerticle.start()
             a. ConfigProvider.createConfig()   (AppVerticle's own config load)
             b. Guice.createInjector(new AppModule(vertx, appConfig))
             c. vertx.createHttpServer()
             d. httpServer.listen(port, host)
             e. startPromise.complete()
  5. vertx.deployVerticle(WorkerVerticle, WORKER)
       └── WorkerVerticle.start()
             a. ConfigProvider.createConfig()   (WorkerVerticle's own config load)
             b. Guice.createInjector(new AppModule(vertx, appConfig))
             c. consumers.forEach(registerConsumer(eventBus))
             d. startPromise.complete()

Shutdown (SIGTERM / shutdown hook):
  1. vertx.undeploy(appVerticleId)
       └── AppVerticle.stop() → httpServer.close()
  2. vertx.undeploy(workerVerticleId)
  3. vertx.close()
  4. latch.await(shutdownTimeoutSeconds)
```

---

## Dependency Injection

`AppModule` is a Guice `AbstractModule`. It receives `Vertx` and `ApplicationConfig` — both are bound as instances, not created by Guice.

**Binding categories:**

| Category | Bound as |
|----------|----------|
| `Vertx`, `ApplicationConfig` | `toInstance()` |
| `ProductRepository` | `to(ProductRepositoryImpl.class).in(Singleton.class)` |
| `UserService`, `ProductService` | `in(Singleton.class)` (constructor injection) |
| `AuthHandler`, `LoggingHandler`, `ErrorHandler` | `in(Singleton.class)` |
| `RouterHelper` | `in(Singleton.class)` |
| `CircuitBreakerRegistry`, `TracingService` | `in(Singleton.class)` |
| `ProbeOrchestrator` | `to(DefaultProbeOrchestrator.class).in(Singleton.class)` |
| `HealthCheckRegistry` | `to(DefaultHealthCheckRegistry.class).in(Singleton.class)` |
| `MetricsFacade` | `@Provides` — conditional: `MicrometerMetricsFacade` or `NoopMetricsFacade` |
| `MetricsScrapeEndpoint` | `@Provides` — cast from `MetricsFacade` or `NoopMetricsFacade` |
| `Router` (Vert.x) | `@Provides` — `Router.router(vertx)` |
| All 6 routers | `in(Singleton.class)` |
| All 4 consumers | `in(Singleton.class)` |
| `RouterConfig` | `in(Singleton.class)` |

**Adding a new service:** Implement the class with `@Inject` constructor, add `bind(MyService.class).in(Singleton.class)` in `AppModule.configure()`.

---

## HTTP Layer

### Middleware Pipeline

```
Request
  ↓ TimeoutHandler(requestTimeoutMs)       — fail with 503 on timeout
  ↓ CorsHandler                            — CORS preflight + headers
  ↓ LoggingHandler                         — log method/path/status/duration
  ↓ AuthHandler                            — demo Bearer presence check
  ↓ [Sub-router matching]
  ↓ Route Handler (via RouterHelper.handleAsync)
  ↓ ErrorHandler (failureHandler)          — ServiceException → JSON error response
```

### Sub-Router Mounting (all routers)

All routers expose `getRouter()` returning a `Router` instance, mounted in `RouterConfig.setupRoutes()`:

```java
router.route("/api/common/*").subRouter(commonRouter.getRouter());
router.route("/*").subRouter(healthRouter.getRouter());
router.route("/*").subRouter(metricsRouter.getRouter());
router.route("/*").subRouter(openApiRouter.getRouter());
router.route("/api/users/*").subRouter(userRouter.getRouter());
router.route("/api/products/*").subRouter(productRouter.getRouter());
```

### Route Handler Pattern

```java
// In any *Router.java:
router.get("/").handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllUsers));

private Uni<Void> getAllUsers(RoutingContext ctx) {
    return userService.getAllUsersWithContext(ctx)
        .onItem().invoke(result -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, result))
        .replaceWithVoid();
}
```

`RouterHelper.handleAsync()` is **static** — it creates a `ContextAwareVertxWrapper`, stores it in `ctx`, logs request/response lifecycle, and routes failures to `ctx.fail()`.

---

## Service Layer

### Pattern: Dual Method

Every service method exists in two forms:

```java
// Called internally or in tests (no context)
public Uni<JsonObject> getAllUsers() {
    return getAllUsersWithContext(null);
}

// Called from routers (with correlation context)
public Uni<JsonObject> getAllUsersWithContext(RoutingContext ctx) {
    ContextAwareVertxWrapper wrapper =
        ctx != null ? ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER) : null;
    // ... business logic wrapped in circuit breaker
}
```

### Circuit Breaker Wrapping

```java
return circuitBreakerRegistry.getDatabaseCircuitBreaker()
    .execute(() -> performGetAllUsers());
```

All service operations go through the circuit breaker. Only `ServiceException` with status >= 500 counts as a failure — 4xx (not found, bad request) do NOT trip the breaker.

### Replacing Stubs With Real Persistence

1. Implement `ProductRepository` with a real DB client (e.g., Vert.x PgClient, JOOQ)
2. Replace `UserService.performGet*()` methods with actual DB calls
3. Remove `ServiceConfig` simulation delay properties (`baseDelayMs`, `userFetchBaseDelayMs`, etc.)
4. Add real `@Inject` constructor with the DB client dependency
5. Bind the new implementation in `AppModule`

---

## EventBus & Worker Layer

```
AppVerticle (event-loop)
  │
  │  vertx.eventBus().send("app.worker.analytics-report", message)
  │
  ▼
WorkerVerticle (worker-pool thread)
  └── AnalyticsConsumer.registerConsumer(eventBus)
        └── eventBus.consumer("app.worker.analytics-report", handler)
```

**Adding a new consumer:**
1. Implement `EventBusConsumer` interface
2. Add `@Inject` constructor
3. Bind in `AppModule`: `bind(MyConsumer.class).in(Singleton.class)`
4. Add `consumers.add(injector.getInstance(MyConsumer.class))` in `WorkerVerticle.createConsumers()`

**Context propagation over EventBus:**
Use `ContextAwareVertxWrapper.enrichEventBusMessage(JsonObject)` before sending to inject correlation IDs, and `ContextAwareVertxWrapper.fromEventBusMessage(vertx, message)` on the consumer side to restore context.

---

## Context & Correlation Tracking

```
HTTP Request arrives
  │
  ▼
RouterHelper.handleAsync(ctx, handler)
  └── ContextAwareVertxWrapper.fromHttpRequest(vertx, ctx)
        └── CorrelationContext.create()
              ├── requestId   = UUID
              ├── correlationId = X-Correlation-ID header (or new UUID)
              ├── traceId     = from TracingService span
              ├── spanId      = from TracingService span
              ├── userId      = from ctx.user() (if authenticated)
              └── tenantId    = from X-Tenant-ID header

ctx.put(ContextKeys.ROUTING_CONTEXT_WRAPPER, wrapper)

Service:
  ContextAwareVertxWrapper wrapper = ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER);
  wrapper.logEvent("service_operation_start", "operation", "getAllUsers");
```

`CorrelationContext.setupLoggingContext()` / `clearLoggingContext()` manages MDC for structured log output.

---

## Circuit Breaker

Custom FSM implementation (not Resilience4j / Vert.x built-in):

```
         failures >= threshold
CLOSED ──────────────────────► OPEN
  ▲                               │
  │    successes >= threshold     │ resetTimeout elapsed
  └──────────────────── HALF_OPEN ◄┘
```

**Configuration (in `ApplicationConfig.ServiceConfig`):**

| Property | Default | Description |
|----------|---------|-------------|
| `circuit-breaker-failure-threshold` | 5 | Failures before OPEN |
| `circuit-breaker-success-threshold` | 3 | Successes in HALF_OPEN to CLOSE |
| `circuit-breaker-timeout-ms` | 10000 | Per-operation timeout |
| `circuit-breaker-reset-timeout-ms` | 60000 | OPEN → HALF_OPEN wait |

**Observer pattern:** Implement `CircuitBreakerObserver` and pass it to `CircuitBreaker` constructor to receive `onTransition()` / `onFailure()` callbacks (e.g., for Micrometer metrics).

---

## Observability Stack

### Health Checks (`/health/*`)

| Endpoint | Check Types | Description |
|----------|-------------|-------------|
| `GET /health` | LIVENESS + READINESS | Combined check |
| `GET /health/liveness` | LIVENESS | Process is alive |
| `GET /health/readiness` | READINESS | Ready to serve traffic |
| `GET /health/detailed` | All | Full details per check |

Checks registered in `DefaultHealthCheckRegistry`:
- `ProcessLivenessCheck` — always UP if process is running
- `EventBusReadinessCheck` — pings `app.health.check` over EventBus
- `StartupCheck` — reflects `ApplicationState` (STARTING/RUNNING/STOPPING)

**Adding a custom health check:**
1. Implement `HealthCheck` interface
2. Annotate with check type via `checkType()`
3. Register: `healthCheckRegistry.register(myCheck)`

### Metrics (`/metrics`)

- Backend: Micrometer + Prometheus
- Access: `protected` by default (requires Bearer token); set `observability.metrics.exposure=open` to make public
- JVM and system metrics included by default (`includeJvm=true`, `includeSystem=true`)
- `MetricsFacade` interface — swap backend without changing calling code

### Tracing (OpenTelemetry)

- W3C Trace Context propagation (`traceparent` / `tracestate` headers)
- `TracingService` injects trace/span IDs into `CorrelationContext` and EventBus messages
- Exporter: `logging` (stdout) or `otlp` (gRPC to collector at `otlpEndpoint`)
- Sampling: ratio-based (`samplingRatio=1.0` = 100% by default)

---

## Configuration Model

```
app:
  server:
    port, host, request-timeout-ms, api-prefix
    cors: { enable, allowed-origins, allow-credentials }
    max-body-size-bytes
  worker:
    pool-size, max-execute-time
  security:
    enable-auth, jwt-secret*, jwt-expiration-ms, public-paths
  logging:
    enable-request-logging, log-request-bodies, log-level,
    include-stack-trace-in-error-response
  service:
    default-timeout-ms
    circuit-breaker-failure-threshold, circuit-breaker-success-threshold,
    circuit-breaker-timeout-ms, circuit-breaker-reset-timeout-ms
    [simulation delays — remove when adding real DB]
  analytics:
    event-address, database-query-delay-ms, file-processing-delay-ms, ...
  event-bus:
    health-check-address, batch-operation-address, legacy-operation-address
  validation:
    max-name-length, batch-processed-records
  deployment:
    enable-event-loop-pool-auto-sizing, worker-pool-size-multiplier,
    max-event-loop-execute-time-ms, blocked-thread-check-interval-ms,
    warning-exception-time-ms, worker-pool-name, shutdown-timeout-seconds
  observability:
    health: { enable, expose-detailed, check-timeout-ms, legacy-aliases }
    metrics: { enable, backend, endpoint-path, exposure, include-jvm, include-system }
    tracing: { enable, service-name, exporter, otlp-endpoint, sampling-ratio }
```

`*` `jwt-secret` default is `"your-secret-key"` — override via `APP_SECURITY_JWT_SECRET` env var before deployment.

All environment variable overrides use standard SmallRye convention: `app.server.port` → `APP_SERVER_PORT`.

---

## Validation Framework

```java
// Define validator (in Validator.java):
public static final io.vertx.ext.web.validation.builder.BodyProcessorFactory Users.CREATE =
    ValidatorBuilder.create()
        .field("name", required(), minLength(1), maxLength(100))
        .field("email", required(), email())
        .build();

// Use in router:
ValidationResult result = Validator.Users.CREATE.validate(ctx.body().asJsonObject());
routerHelper.handleValidationErrors(result);  // throws ServiceException(400) if invalid
```

Available rules: `required`, `minLength(n)`, `maxLength(n)`, `email`, `positiveNumber`, `integerRange(min, max)`.

---

## Technology Stack

| Component | Library | Version |
|-----------|---------|---------|
| HTTP / EventBus | Eclipse Vert.x | 4.5.14 |
| Reactive streams | SmallRye Mutiny | 2.6.2 |
| Dependency injection | Google Guice | 7.0.0 |
| Configuration | SmallRye Config | 3.13.4 |
| Metrics | Micrometer + Prometheus | 1.12.12 |
| Tracing | OpenTelemetry SDK | 1.32.0 |
| Logging | SLF4J + Logback | 1.5.13 |
| Boilerplate | Project Lombok | 1.18.32 |
| Build | Gradle | 9.0 |
| Formatting | Spotless + Palantir Java Format | 6.25.0 |
| Testing | JUnit 5 + vertx-junit5 | 5.9.1 |
| Coverage | JaCoCo | 0.8.12 |

---

## Extending the Template

### Add a new domain (e.g., Orders)

1. Create `services/OrderService.java` with `@Inject` constructor + `Uni<T>` methods
2. Create `repositories/OrderRepository.java` (interface) + `OrderRepositoryImpl.java`
3. Create `web/rests/OrderRouter.java` implementing `getRouter()` pattern
4. Register in `AppModule`: bind service, repository, router as `@Singleton`
5. Mount in `RouterConfig.setupRoutes()`: `router.route("/api/orders/*").subRouter(orderRouter.getRouter())`
6. Add constants to `PathConstants`, `MessageConstants` as needed

### Replace stub auth with real JWT

1. Add JWT library dependency (e.g., `io.vertx:vertx-auth-jwt`)
2. Rewrite `AuthHandler.authenticateRequest()` to parse and verify the JWT
3. Set user principal on `ctx.setUser()` for downstream access
4. Update `SecurityConfig` with real secret/key configuration

### Add a real database

1. Add DB client dependency (e.g., `io.vertx:vertx-pg-client` for PostgreSQL)
2. Configure connection pool in `AppModule` as a `@Provides @Singleton` binding
3. Implement `ProductRepository` and `UserRepository` with real SQL
4. Remove simulation delay properties from `ServiceConfig`

### Wire circuit breaker metrics to Micrometer

1. Implement `CircuitBreakerObserver`:
   ```java
   public class MicrometerCircuitBreakerObserver implements CircuitBreakerObserver {
       @Override
       public void onTransition(String name, State from, State to) {
           Metrics.gauge("circuit_breaker.state", ..., to.ordinal());
       }
   }
   ```
2. Inject `MetricsFacade` / `MeterRegistry` and pass observer to `CircuitBreakerRegistry`

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Runtime Requirements

- **Java:** `21.0.7-graal` (GraalVM) — use SDKMAN: `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env`
- **Gradle wrapper:** 9.0.0 (via `./gradlew` — ignore the `gradle=8.14.4` in `.sdkmanrc`, that's outdated)

## Development Commands

```bash
./gradlew build                              # compile + test + spotless check
./gradlew run                                # run the application (port 8081)
./gradlew test                               # run all tests
./gradlew test --tests ClassName.methodName  # run single test
./gradlew clean check                        # full validation: tests + JaCoCo coverage gate
./gradlew spotlessCheck                      # check formatting (fails CI if violated)
./gradlew spotlessApply                      # auto-format all Java files
./gradlew generateOpenApiSpec                # generate openapi.yaml / openapi.json artifacts
```

JaCoCo coverage gate: 80% line coverage required on `UserService`, `ProductService`, `Validator`.

## Architecture Overview

### Deployment Model (Kubernetes-native)

**1 pod = 1 JVM = 1 AppVerticle + 1 WorkerVerticle.** Horizontal scaling is done at the pod level by Kubernetes. Never configure `setInstances(n > 1)` in verticle deployment.

```
StartupApp
  ├── AppVerticle  (event-loop, HTTP :8081)
  │     └── RouterConfig → middleware pipeline → sub-routers
  └── WorkerVerticle  (worker-pool, EventBus consumers)
        └── AnalyticsConsumer | BatchOperationConsumer | HealthCheckConsumer | LegacyOperationConsumer
```

### Startup & Config

`StartupApp.main()` loads config **once** into `cachedConfig` (static field). `AppModule` takes `ApplicationConfig` as a constructor argument — it does **not** call `ConfigProvider.createConfig()` internally. Do not add extra `ConfigProvider.createConfig()` call sites.

Vert.x options (thread pool sizes) are derived from `app.deployment.*` and `app.worker.*`. Instance count is always `1` — no auto-sizing logic remains.

### Dependency Injection

`AppModule` is the single Guice module. Everything bound as `@Singleton` via constructor injection (`@Inject`). Key `@Provides` factory methods:
- `Router` — `Router.router(vertx)` (must be a factory call)
- `MetricsFacade` — conditionally `MicrometerMetricsFacade` or `NoopMetricsFacade`
- `MetricsScrapeEndpoint` — cast from `MetricsFacade` or `NoopMetricsFacade`

**Adding a new dependency:** write `@Inject` constructor, add `bind(MyClass.class).in(Singleton.class)` in `AppModule.configure()`.

### HTTP Middleware Pipeline

`RouterConfig` wires in this exact order:

```
TimeoutHandler → CorsHandler → LoggingHandler → AuthHandler → [sub-routers] → ErrorHandler (failureHandler)
```

All routers (domain and infrastructure) expose `getRouter()` and are mounted as sub-routers in `RouterConfig.setupRoutes()`. There is no `configureRoutes(Router)` pattern — every router uses sub-router composition.

### Request Handler Pattern

```java
router.get("/path").handler(ctx -> RouterHelper.handleAsync(ctx, this::myHandler));

private Uni<Void> myHandler(RoutingContext ctx) {
    return myService.doWithContext(ctx)
        .onItem().invoke(result -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, result))
        .replaceWithVoid();
}
```

`RouterHelper.handleAsync()` is **static**. It creates a `ContextAwareVertxWrapper`, stores it at `ContextKeys.ROUTING_CONTEXT_WRAPPER`, logs lifecycle events, and routes all failures to `ctx.fail()` → `ErrorHandler`.

### Service Layer

Services follow a **dual-method pattern**: `method(args)` (no context, delegates with null) → `methodWithContext(args, RoutingContext ctx)` (with correlation tracking). All operations are wrapped in a circuit breaker:

```java
return circuitBreakerRegistry.getDatabaseCircuitBreaker().execute(() -> performOp());
```

Only `ServiceException` with HTTP status ≥ 500 counts as a circuit-breaker failure. 4xx errors do not trip the breaker.

### EventBus Worker Communication

HTTP-side routes use `eventBus().request(address, payload)` with reply/failure mapping. Consumer-side uses `message.reply(...)` on success or `message.fail(statusCode, reason)` on failure; `ReplyException.failureCode()` is mapped back to the HTTP status.

See `claudedocs/eventbus_interaction.md` for sequence diagrams and troubleshooting table.

**Active addresses:**

| Address | Consumer | Notes |
|---------|----------|-------|
| `app.worker.analytics-report` | `AnalyticsConsumer` | triggered by `GET /api/products/analytics/report` |
| `app.worker.batch-operation` | `BatchOperationConsumer` | valid ops: `insert`, `update`, `delete` (requires `confirmDelete=true`), `migrate` |
| `app.health.check` | `HealthCheckConsumer` | readiness probe over EventBus |
| `app.worker.operation` | `LegacyOperationConsumer` | legacy path |

### Context & Correlation

`ContextAwareVertxWrapper.fromHttpRequest()` seeds a `CorrelationContext` with request ID, correlation ID (from `X-Correlation-ID` header or new UUID), trace/span IDs, source IP, and user-agent. Before EventBus sends, call `wrapper.enrichEventBusMessage(msg)` to inject `_context`, `_correlationId`, `_requestId`. On the consumer side, call `ContextAwareVertxWrapper.fromEventBusMessage(vertx, msg)` to restore context.

### Circuit Breaker

Custom FSM: `CLOSED → OPEN → HALF_OPEN → CLOSED`. Config keys under `app.service.*`:
- `circuit-breaker-failure-threshold` (default 5)
- `circuit-breaker-success-threshold` (default 3, for HALF_OPEN → CLOSED)
- `circuit-breaker-timeout-ms` (default 10 000)
- `circuit-breaker-reset-timeout-ms` (default 60 000, OPEN → HALF_OPEN wait)

## API Routes

**Public (no auth):**
- `GET /api/common`
- `GET /health`, `/health/live`, `/health/ready`, `/health/started`
- `/health/liveness`, `/health/readiness` — aliases when `app.observability.health.legacy-aliases=true`
- `GET /health/detailed` — when `app.observability.health.expose-detailed=true`
- `GET /openapi.yaml`, `GET /openapi.json`, `GET /docs` (Swagger UI)
- `GET /metrics` — only when `app.observability.metrics.exposure=open`

**Protected (require `Authorization: Bearer <token>`):**
- `GET|POST|PUT|DELETE /api/users`, `/api/users/:id`
- `GET|POST /api/products`, `/api/products/:productId`, `/api/products/:productId/stock`
- `GET /api/products/analytics/report`, `POST /api/products/batch/:operation`
- `GET /metrics` — default (`exposure=protected`)

**Health model:** `GET /health` (overall) = `ready + started`; liveness is excluded from the combined check.

## Observability Stack

- **Metrics:** Micrometer + Prometheus. `MetricsFacade` interface — `MicrometerMetricsFacade` or `NoopMetricsFacade`. Scrape at `/metrics`.
- **Tracing:** OpenTelemetry SDK, W3C trace context. `TracingService` creates HTTP server spans and EventBus consumer spans. `X-Trace-ID` emitted in HTTP responses. Exporter: `logging` (default) or `otlp` via `app.observability.tracing.otlp-endpoint`.
- **Health:** `DefaultProbeOrchestrator` + `DefaultHealthCheckRegistry`. Three built-in checks: `ProcessLivenessCheck`, `EventBusReadinessCheck`, `StartupCheck`.

## Configuration

Config interface: `ApplicationConfig` (`@ConfigMapping(prefix = "app")`). Source: `src/main/resources/application.yml`. Override with env vars (`APP_SERVER_PORT`, `APP_SECURITY_JWT_SECRET`, etc.).

Key non-obvious defaults:
- `app.security.jwt-secret=your-secret-key` — **override before any deployment**
- `app.observability.metrics.exposure=protected` — metrics require Bearer token by default
- `app.observability.tracing.exporter=logging` — traces go to stdout, not OTLP
- `app.server.cors.allowed-origins=*` — tighten for production

OpenAPI source contract: `src/main/resources/META-INF/openapi.yaml`. Generated artifacts are placed under `build/generated/openapi/` and copied into `openapi/` in the final JAR via `processResources`.

## Constants & Error Handling

- HTTP status codes → `HttpStatusCodes.*`
- Error messages → `MessageConstants.*`
- Context keys → `ContextKeys.*`
- Paths → `PathConstants.*`
- EventBus addresses → `EventBusConstants.*`
- **Never wildcard-import** `com.github.kaivu.vertxweb.constants.*` — use explicit per-class imports
- Throw `ServiceException(message, HttpStatusCodes.*)` to return structured JSON errors; `ErrorHandler` converts it automatically

## Stub Data (Replace for Production)

| Component | Current state |
|-----------|--------------|
| `UserService` | Switch-case for users 1–3; simulated delays |
| `ProductRepositoryImpl` | In-memory Map with 3 products |
| `AuthHandler` | Bearer token presence check only (DEMO mode) |

Remaining work before production: real JWT verification in `AuthHandler`, real DB-backed repositories, CORS policy tightening, test coverage expansion to 80%+ project-wide.

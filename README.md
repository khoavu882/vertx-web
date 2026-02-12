# Vertx Web

![Vert.x 4.5.14](https://img.shields.io/badge/vert.x-4.5.14-purple.svg)

Reactive Vert.x + Guice demo backend with dual-verticle runtime and EventBus-driven worker processing.

## Runtime And Tooling

- Java: `21.0.7-graal` (see `.sdkmanrc`)
- Gradle: `8.14.4` (see `.sdkmanrc`)
- Main entrypoint: `src/main/java/com/github/kaivu/vertxweb/StartupApp.java`
- Main class for Gradle run: `com.github.kaivu.vertxweb.StartupApp`

## Build, Run, Validate

- Run with SDKMAN env: `source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env`
- Build: `./gradlew build`
- Run: `./gradlew run`
- Format check: `./gradlew spotlessCheck`
- Format apply: `./gradlew spotlessApply`
- Full validation: `./gradlew clean check`
- Generate OpenAPI artifacts: `./gradlew generateOpenApiSpec`

## Architecture Review Status (2026-02-12)

Aligned with `claudedocs/comprehensive_architectural_review.md`:

- Completed:
  - OpenAPI documentation endpoints and Swagger UI (`/openapi.yaml`, `/openapi.json`, `/docs`)
  - Standardized router sub-router composition (`getRouter()` + mount from `RouterConfig`)
  - Distributed tracing integration (OpenTelemetry + trace header exposure)
  - Circuit breaker usage consistency across user/product service operations
  - Domain-based constants split into dedicated classes under `constants/`
- Remaining blockers:
  - JWT verification/auth hardening (current auth is demo-only)
  - Real database integration (current data layer is simulated/in-memory)
  - Comprehensive test coverage target (80%+) and coverage gating
- Important pending:
  - Production CORS policy tightening (default still uses wildcard origins)

## Architecture Overview

The service runs with two verticle roles:

- `AppVerticle` serves HTTP requests and hosts the web router stack.
- `WorkerVerticle` registers EventBus consumers for heavier async/business tasks.

Startup flow:

1. `StartupApp` loads config using `ConfigProvider`.
2. Vert.x options are derived from `app.deployment` and `app.worker` settings.
3. App and worker verticles are deployed with auto-sizing options.
4. Shutdown hook performs graceful undeploy and Vert.x close.

Dependency injection and composition are configured in:

- `src/main/java/com/github/kaivu/vertxweb/config/AppModule.java`
- `src/main/java/com/github/kaivu/vertxweb/web/routes/RouterConfig.java`

## HTTP Processing Pipeline

Global handler order in `RouterConfig`:

1. `TimeoutHandler`
2. Global `CorsHandler` (config-driven)
3. `LoggingHandler` (also emits HTTP metrics)
4. `AuthHandler`
5. Route handlers
6. Global failure handler (`ErrorHandler`)

Current behavior:

- CORS is applied globally.
- `OPTIONS` is always auth-bypassed for preflight.
- Authentication is intentionally demo-only (Bearer token presence check only).

## Routes And Exposure Model

Core API routes:

- Public by design:
  - `GET /api/common`
  - `GET /health`
  - `GET /health/live`
  - `GET /health/ready`
  - `GET /health/started`
  - Optional aliases when enabled: `GET /health/liveness`, `GET /health/readiness`
  - Optional detailed endpoint when enabled: `GET /health/detailed`
- Conditionally public:
  - `GET /metrics` is public only when `app.observability.metrics.exposure=open`
- Public API docs:
  - `GET /openapi.yaml`
  - `GET /openapi.json`
  - `GET /docs` (Swagger UI)
- Protected by auth middleware:
  - `GET /api/users`
  - `GET /api/users/:id`
  - `POST /api/users`
  - `PUT /api/users/:id`
  - `DELETE /api/users/:id`
  - `GET /api/products`
  - `GET /api/products/:productId`
  - `POST /api/products`
  - `PUT /api/products/:productId/stock`
  - `GET /api/products/analytics/report`
  - `POST /api/products/batch/:operation`

## Observability Architecture

Observability modules live under:

- `src/main/java/com/github/kaivu/vertxweb/observability/health`
- `src/main/java/com/github/kaivu/vertxweb/observability/metrics`
- `src/main/java/com/github/kaivu/vertxweb/observability/tracing`

Health model:

- Canonical probes: `live`, `ready`, `started`, `overall`.
- `overall` is composed from `ready + started` (liveness excluded).
- Probe orchestration is handled by `DefaultProbeOrchestrator` and `DefaultHealthCheckRegistry`.

Metrics model:

- Backend is selected by config (`micrometer` currently wired).
- Scrape endpoint abstraction: `MetricsScrapeEndpoint`.
- Default endpoint path is `/metrics`.
- Default exposure in `application.yml` is `protected`.

EventBus and circuit-breaker metrics are recorded through `MetricsFacade`.

Tracing model:

- `TracingService` creates HTTP server spans and EventBus consumer spans.
- `X-Trace-ID` is propagated in HTTP responses when tracing is enabled.
- Exporter is config-driven (`logging` or `otlp`) via `app.observability.tracing`.

## EventBus Structure

Active addresses:

- `app.worker.analytics-report`
- `app.worker.batch-operation`
- `app.health.check`
- `app.worker.operation` (legacy path)

See `claudedocs/eventbus_interaction.md` for interaction detail and workflow diagram.

## Project Structure

Key directories:

- `src/main/java/com/github/kaivu/vertxweb/config` for config mapping, loader, DI module
- `src/main/java/com/github/kaivu/vertxweb/verticles` for app/worker verticle entrypoints
- `src/main/java/com/github/kaivu/vertxweb/web` for router config, REST routers, validation
- `src/main/resources/META-INF/openapi.yaml` for source OpenAPI contract
- `src/main/java/com/github/kaivu/vertxweb/consumers` for worker-side EventBus handlers
- `src/main/java/com/github/kaivu/vertxweb/services` for business service logic
- `src/main/java/com/github/kaivu/vertxweb/patterns` for circuit breaker and registry
- `src/main/java/com/github/kaivu/vertxweb/constants` for split domain/base constants
- `src/main/java/com/github/kaivu/vertxweb/observability` for health and metrics abstractions
- `src/test/java/com/github/kaivu/vertxweb/integration` for integration tests
- `src/main/resources/application.yml` for default runtime config

## Configuration

Configuration uses SmallRye `@ConfigMapping(prefix = "app")` in:

- `src/main/java/com/github/kaivu/vertxweb/config/ApplicationConfig.java`

Sources and behavior:

- Primary source: `src/main/resources/application.yml`
- System properties and env vars override defaults (example: `APP_SERVER_PORT`)
- `ConfigProvider` enforces YAML-source presence at startup

Current shape includes nested CORS and observability objects:

```yaml
app:
  server:
    cors:
      enable: true
      allowed-origins: "*"
      allow-credentials: false
  observability:
    health:
      enable: true
      expose-detailed: true
      check-timeout-ms: 1000
      legacy-aliases: true
    metrics:
      enable: true
      backend: micrometer
      endpoint-path: /metrics
      exposure: protected
      include-jvm: true
      include-system: true
```

## Tests

Current test suites:

- `src/test/java/com/github/kaivu/vertxweb/integration/ObservabilityRoutesIntegrationTest.java`
- `src/test/java/com/github/kaivu/vertxweb/services/UserServiceTest.java`
- `src/test/java/com/github/kaivu/vertxweb/services/ProductServiceCircuitBreakerTest.java`
- `src/test/java/com/github/kaivu/vertxweb/web/validation/ValidatorTest.java`

Current covered contracts:

- `/metrics` auth behavior (`protected` vs `open`)
- Health canonical routes
- Health alias enable/disable behavior
- OpenAPI/docs endpoint exposure (`/openapi.yaml`, `/openapi.json`, `/docs`)
- Trace header behavior (`X-Trace-ID` enabled/disabled)
- Circuit breaker timeout/open-state behavior (user endpoints)
- Service validation and error-path unit assertions

## Important Note

Security is intentionally weak for demo/boilerplate use. Replace auth behavior in `AuthHandler` before any non-demo deployment.

## References

- [Vert.x Docs](https://vertx.io/docs/)
- [SmallRye Config](https://smallrye.io/smallrye-config/)
- [SmallRye OpenAPI](https://github.com/smallrye/smallrye-open-api)
- [Micrometer](https://micrometer.io/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)

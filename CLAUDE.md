# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

- **Build:** `./gradlew build`
- **Lint/Format:** `./gradlew spotlessCheck` (check), `./gradlew spotlessApply` (auto-format)
- **Test:** `./gradlew test`
- **Test (single):** `./gradlew test --tests ClassName.methodName`
- **Validate:** `./gradlew clean test check` (full validation)
- **Run app:** `./gradlew run`

**Note:** Test dependencies are configured (JUnit 5 + Vert.x JUnit5 extension), but no test source files exist yet.

## Architecture Overview

### Application Bootstrap
The application uses a dual-verticle architecture with auto-scaling:
- `StartupApp.java` - Entry point that creates `Vertx` instance with tuned `VertxOptions`, deploys both verticles, and sets up graceful shutdown
- `AppVerticle` - Main HTTP server verticle. Multiple instances deployed based on CPU cores (configurable via `app.deployment.*`). Each instance creates its own Guice `Injector` and `RouterConfig`
- `WorkerVerticle` - Background worker deployed with `ThreadingModel.WORKER`. Registers all `EventBusConsumer` implementations

### Dependency Injection
- Uses Google Guice (`AppModule.java`)
- `AppModule` receives `Vertx` instance and loads config via `ConfigProvider.createConfig()`
- All services, routers, middleware, and consumers bound as `@Singleton` in `configure()`
- `Router` provided via `@Provides` factory method since it requires `Router.router(vertx)`
- Both `AppVerticle` and `WorkerVerticle` create their own `Injector` independently

### HTTP Routing Architecture
- `RouterConfig.java` - Central router configuration, all dependencies constructor-injected
- Middleware pipeline order: `TimeoutHandler` → `CorsHandler` (global) → `LoggingHandler` → `AuthHandler` → Route handlers → `ErrorHandler` (failure handler)
- API prefix: `/api` (configured via `app.server.api-prefix`)
- CORS is global for all routes (configured via `app.server.cors.*`)
- OPTIONS requests bypass auth automatically for CORS preflight support
- Auth is **demo-only by design**: Bearer token presence check only, no JWT verification
- Route structure:
  - `/api/common/*` - Public routes (bypasses auth via `publicPaths` config check in `AuthHandler`)
  - `/api/users/*` - Protected user endpoints (sub-router)
  - `/api/products/*` - Protected product endpoints (sub-router)
  - `/health/*` - Health check routes (configured directly on main router, not as sub-router)
  - `/metrics` - Metrics endpoint (configured via `MetricsRouter`, protected or public based on config)
- Each domain router (`UserRouter`, `ProductRouter`, `CommonRouter`) creates its own `Router.router(vertx)` and exposes it via `getRouter()`
- `HealthRouter` and `MetricsRouter` use a different pattern: `configureRoutes(Router)` adds routes directly to the main router

### Request Handling Pattern
All endpoints use `RouterHelper.handleAsync()` which:
1. Creates a `ContextAwareVertxWrapper` from the HTTP request
2. Extracts user/tenant context from headers (`X-Tenant-ID`)
3. Stores the wrapper in `RoutingContext` as `"contextWrapper"`
4. Subscribes to the `Uni<Void>` returned by the handler
5. Logs request lifecycle with correlation IDs
6. Routes failures through `ctx.fail()` to the `ErrorHandler`

Router handler pattern:
```java
router.get().handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllUsers));

private Uni<Void> getAllUsers(RoutingContext ctx) {
    return userService.getAllUsersWithContext(ctx)
        .onItem().invoke(users -> RouterHelper.sendJsonResponse(ctx, AppConstants.Status.OK, users))
        .replaceWithVoid();
}
```

### Service Layer
- All services use Mutiny `Uni<T>` for reactive async operations
- Dual method pattern: `methodName()` (legacy, passes `null` ctx) delegates to `methodNameWithContext(args, RoutingContext ctx)` (production, with correlation tracking)
- Services extract `ContextAwareVertxWrapper` from `ctx.get("contextWrapper")` for correlation logging
- Currently uses simulated/stubbed data (no real database) with configurable delays to simulate latency
- `UserService` wraps operations with `CircuitBreakerRegistry.getDatabaseCircuitBreaker()`
- `ProductService` delegates to `ProductRepository` interface (implemented by `ProductRepositoryImpl`)

### Validation Framework
- `Validator` class holds static validator instances per entity: `Validator.Users.CREATE`, `Validator.Products.CREATE`, etc.
- Built from composable `ValidationRule` instances (`required`, `minLength`, `maxLength`, `email`, `positiveNumber`, `integerRange`)
- Returns `ValidationResult` checked via `routerHelper.handleValidationErrors()`

### Circuit Breaker
- Custom implementation in `patterns/CircuitBreaker.java` (not Vert.x circuit breaker)
- States: `CLOSED` → `OPEN` → `HALF_OPEN` → `CLOSED`
- Configured via `CircuitBreakerConfig` record (failure threshold, success threshold, timeouts)
- `CircuitBreakerRegistry` provides named circuit breakers (e.g., `getDatabaseCircuitBreaker()`)
- Wraps `Uni<T>` operations with timeout and failure counting

### EventBus Consumer Pattern
- Interface: `EventBusConsumer` with `getEventAddress()` and `registerConsumer(EventBus)`
- Consumers: `AnalyticsConsumer`, `BatchOperationConsumer`, `HealthCheckConsumer` (Guice-injected), `LegacyOperationConsumer` (manually instantiated)
- All injected consumers receive `ApplicationConfig` via constructor
- Registered in `WorkerVerticle.createConsumers()`

### Context & Correlation
- `ContextAwareVertxWrapper` extends `VertxWrapper` - provides correlation tracking across verticle boundaries
- `CorrelationContext` carries request ID, correlation ID, user/tenant context, timing data
- Supports MDC integration for structured logging
- EventBus messages enriched with `_context`, `_correlationId`, `_requestId` fields via `enrichEventBusMessage()`
- Factory methods: `fromHttpRequest()`, `fromEventBus()`, `fromEventBusMessage()`

## Configuration

- SmallRye Config with `@ConfigMapping(prefix = "app")` on `ApplicationConfig` interface
- Config file: `src/main/resources/application.yml`
- Loaded via `ConfigProvider.createConfig()` (uses SmallRye `ConfigProviderResolver`)
- YAML-source presence is explicitly validated at startup
- Environment variable override: `app.server.port` → `APP_SERVER_PORT`
- All interfaces have `@WithDefault` annotations for sensible defaults
- Config sections: `server`, `worker`, `security`, `logging`, `service`, `analytics`, `validation`, `deployment`, `observability`
  - `server.cors`: CORS configuration (allowed origins, credentials, enabled/disabled)
  - `server.request-timeout-ms`: Request timeout for `TimeoutHandler`
  - `observability.health`: Health check configuration
  - `observability.metrics`: Micrometer metrics endpoint configuration

## Build System
- **Gradle 9.0** with wrapper scripts
- Version properties in `gradle.properties` (Vert.x 4.5.14, Mutiny 2.6.2, Guice 7.0.0, SmallRye Config 3.13.4, Micrometer 1.12.12)
- Main class: `com.github.kaivu.vertxweb.StartupApp`
- Spotless plugin with Palantir Java Format enforced

## API Routes

**Public routes:**
- `GET /api/common`
- `GET /health`, `/health/readiness`, `/health/liveness`, `/health/detailed`
- `GET /metrics` (if `observability.metrics.exposure` is `public`)

**Protected routes:**
- `GET /api/users`, `GET /api/users/:id`, `POST /api/users`, `PUT /api/users/:id`, `DELETE /api/users/:id`
- `GET /api/products`, `GET /api/products/:productId`, `POST /api/products`, `PUT /api/products/:productId/stock`
- `GET /api/products/analytics/report`, `POST /api/products/batch/:operation`

## EventBus Addresses

- `app.worker.analytics-report` - Analytics report generation (configured via `app.analytics.event-address`)
- `app.worker.batch-operation` - Batch operations processing
- `app.health.check` - Health check consumer
- `app.worker.operation` - Legacy operation consumer

## Constants & Status Codes

Use `AppConstants.Status.*` for HTTP status codes and `AppConstants.Messages.*` for error messages. Never hardcode status codes in exceptions. All timing/delay values must come from `ApplicationConfig`.

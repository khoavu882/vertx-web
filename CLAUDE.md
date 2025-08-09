# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

- **Build:** `./gradlew build`
- **Lint/Format:** `./gradlew spotlessCheck` (check), `./gradlew spotlessApply` (auto-format)
- **Test:** `./gradlew test`
- **Test (single):** `./gradlew test --tests ClassName.methodName`
- **Run app:** `./gradlew run`

## Architecture Overview

### Application Bootstrap
The application uses a dual-verticle architecture:
- `StartupApp.java` - Entry point that deploys both verticles
- `AppVerticle` - Main HTTP server verticle (port 8080)
- `WorkerVerticle` - Background worker pool (10 threads)

### Dependency Injection
- Uses Google Guice for DI (`AppModule.java`)
- All services and routers are `@Singleton` and provided through `@Provides` methods
- Injector is created in `AppVerticle` and used to bootstrap `RouterConfig`

### HTTP Routing Architecture
- `RouterConfig.java` - Central router configuration with middleware pipeline
- Middleware order: AuthHandler → LoggingHandler → Route handlers → ErrorHandler (failures)
- API prefix: `/api`
- Route structure:
  - `/api/common/*` - Public routes (bypasses auth)
  - `/api/users/*` - Protected user endpoints
  - `/api/products/*` - Protected product endpoints

### Code Organization
- `middlewares/` - Request processing (auth, logging, error handling)
- `services/` - Business logic layer (uses async Mutiny for reactive programming)
- `web/rests/` - REST router classes (one per domain)
- `web/exceptions/` - Custom exception types
- `constants/` - Application constants

### Key Technologies
- **Vert.x 4.5.14** - Main framework
- **Google Guice 7.0.0** - Dependency injection
- **Mutiny 2.6.2** - Reactive programming
- **Lombok** - Code generation
- **Spotless + Palantir Java Format** - Code formatting

### Testing
- No test directory exists currently
- When adding tests, use JUnit 5 with Vert.x JUnit5 extension
- Test dependencies already configured in `build.gradle`

### Build System
- **Gradle 9.0** with wrapper scripts
- Version properties defined in `gradle.properties`
- Main class: `com.github.kaivu.vertxweb.StartupApp`
- Code formatting enforced via Spotless plugin

## Configuration Management

### ApplicationConfig
The application uses SmallRye Config with `@ConfigMapping` for type-safe configuration:
- **Location:** `com.github.kaivu.vertxweb.config.ApplicationConfig`
- **Prefix:** `app.*`
- **Environment Variables:** Automatically mapped (e.g., `app.server.port` → `APP_SERVER_PORT`)

### Configuration Sections
```properties
# Server Configuration
app.server.port=8080
app.server.host=0.0.0.0
app.server.request-timeout-ms=30000
app.server.api-prefix=/api

# Worker Configuration
app.worker.pool-size=10
app.worker.max-execute-time=60000

# Security Configuration
app.security.enable-auth=true
app.security.jwt-secret=your-secret-key
app.security.jwt-expiration-ms=86400000
app.security.public-paths=/api/common

# Service Timing Configuration
app.service.base-delay-ms=100
app.service.max-delay-variance-ms=200
app.service.user-fetch-base-delay-ms=50
app.service.create-base-delay-ms=200
app.service.product-create-base-delay-ms=300
app.service.update-base-delay-ms=150
app.service.delete-base-delay-ms=100
app.service.min-id-range=1000
app.service.max-id-range=9999

# Analytics Configuration
app.analytics.event-address=app.worker.analytics-report
app.analytics.database-query-delay-ms=2000
app.analytics.file-processing-delay-ms=1000
app.analytics.execution-timeout-ms=500
app.analytics.max-products=1000
app.analytics.min-products=100
app.analytics.max-revenue=100000
app.analytics.max-order-value=500
app.analytics.min-order-value=50
app.analytics.request-expiration-ms=3600000

# Validation Configuration
app.validation.max-name-length=100
app.validation.batch-processed-records=1000

# Logging Configuration
app.logging.enable-request-logging=true
app.logging.log-request-bodies=false
app.logging.log-level=INFO
```

## Context Management & Traceability

### ContextAwareVertxWrapper
Production-grade correlation context system for request traceability:
- **Location:** `com.github.kaivu.vertxweb.context.ContextAwareVertxWrapper`
- **Usage:** Automatically injected via `RouterHelper.handleAsync()`
- **Features:**
  - Request/Correlation ID tracking
  - User & tenant context
  - Performance monitoring
  - MDC integration for structured logging
  - EventBus message enrichment

### Service Pattern
All services support both context-aware and legacy patterns:
```java
// Legacy method (backward compatibility)
public Uni<JsonObject> createUser(JsonObject user) {
    return createUserWithContext(user, null);
}

// Context-aware method (production tracing)
public Uni<JsonObject> createUserWithContext(JsonObject user, RoutingContext ctx) {
    ContextAwareVertxWrapper wrapper = ctx != null 
        ? (ContextAwareVertxWrapper) ctx.get("contextWrapper") 
        : null;
    // ... implementation with correlation tracking
}
```

### Router Integration
All routers automatically get correlation context:
```java
// Automatic context injection for all endpoints
router.get("/users").handler(ctx -> RouterHelper.handleAsync(ctx, this::getAllUsers));
```

## EventBus Consumer Pattern

### Consumer Architecture
Scalable consumer pattern for WorkerVerticle operations:
- **Base Interface:** `EventBusConsumer`
- **Pattern:** `<BusinessName>Consumer` (e.g., `AnalyticsConsumer`, `BatchOperationConsumer`)
- **Dependency Injection:** All consumers receive `ApplicationConfig` via constructor

### Consumer Implementation
```java
@Singleton
public class MyBusinessConsumer implements EventBusConsumer {
    private final ApplicationConfig appConfig;
    
    @Inject
    public MyBusinessConsumer(ApplicationConfig appConfig) {
        this.appConfig = appConfig;
    }
    
    @Override
    public String getEventAddress() {
        return appConfig.myBusiness().eventAddress();
    }
    
    @Override
    public void registerConsumer(EventBus eventBus) {
        eventBus.<JsonObject>consumer(getEventAddress(), this::handle);
    }
}
```

## Production Features

### Request Traceability
- Every request automatically gets correlation ID
- All service operations logged with correlation context
- EventBus messages enriched with tracing data
- Cross-service request tracking

### Performance Monitoring
- Automatic duration tracking for all operations
- Request processing time measurement
- Service operation timing logs
- Context-aware performance metrics

### Error Correlation
- All errors include correlation context
- Structured error logging with request IDs
- Failed request traceability
- Production-ready error responses with correlation IDs

### Multi-Tenant Support
- Tenant context extraction from `X-Tenant-ID` header
- Tenant isolation through correlation context
- Tenant-aware logging and tracing

### Production Observability
- MDC (Mapped Diagnostic Context) integration
- Structured logging format
- Ready for ELK stack, Prometheus, Grafana
- Correlation-aware monitoring

## Constants & Status Codes

### AppConstants.Status
All HTTP status codes centralized:
- `OK = 200`, `CREATED = 201`
- `BAD_REQUEST = 400`, `UNAUTHORIZED = 401`, `NOT_FOUND = 404`, `GONE = 410`
- `INTERNAL_SERVER_ERROR = 500`, `SERVICE_UNAVAILABLE = 503`

### Best Practices
- Never use hardcoded status codes in exceptions
- Always use `AppConstants.Status.*` constants
- All timing/delay values configured via ApplicationConfig
- All validation limits externalized to config
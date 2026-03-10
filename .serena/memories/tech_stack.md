# Tech Stack

## Core Framework
- **Language:** Java 17+
- **Build:** Gradle 9.0
- **HTTP / EventBus:** Eclipse Vert.x 4.5.14 (`vertx-core`, `vertx-web`)
- **Reactive:** SmallRye Mutiny 2.6.2 (`Uni<T>` / `Multi<T>`)
- **DI:** Google Guice 7.0.0

## Configuration
- **Library:** SmallRye Config 3.13.4
- **Format:** YAML (`application.yml`) + Environment Variables
- **Interface:** `@ConfigMapping(prefix = "app")` on `ApplicationConfig`
- **Pattern:** Config loaded once in `StartupApp`, passed into `AppModule(Vertx, ApplicationConfig)` — never re-parsed inside the module

## Observability
- **Metrics:** Micrometer 1.12.12 + Prometheus registry
- **Tracing:** OpenTelemetry SDK 1.32.0 (W3C propagation; logging or OTLP exporter)
- **Health:** Custom `ProbeOrchestrator` / `HealthCheckRegistry` with LIVENESS / READINESS / STARTUP check types

## Logging
- **Facade:** SLF4J
- **Implementation:** Logback Classic 1.5.13
- **MDC:** `CorrelationContext.setupLoggingContext()` / `clearLoggingContext()`

## Development Tools
- **Boilerplate:** Project Lombok 1.18.32
- **Formatting:** Spotless 6.25.0 + Palantir Java Format (enforced in CI via `spotlessCheck`)
- **Testing:** JUnit 5 (5.9.1) + `vertx-junit5`
- **Coverage:** JaCoCo 0.8.12 — 80% line coverage required on `UserService`, `ProductService`, `Validator`

## Version Properties File
`gradle.properties` — all library versions defined there; reference via `project.property('vertxVersion')` etc.

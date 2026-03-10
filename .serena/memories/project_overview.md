# Project Overview

**Project:** Vert.x Web Application Template
**Purpose:** High-performance reactive web application template for Kubernetes-native single-instance deployment.
**Architecture:** Dual-verticle, single-instance per pod (K8s handles horizontal scaling).

## Verticle Model
- `AppVerticle` — HTTP server on event-loop thread (1 instance per pod). Stores `HttpServer`; overrides `stop()` for graceful close.
- `WorkerVerticle` — Background EventBus consumers on worker-pool thread (1 instance per pod).
- `StartupApp` — Entry point. Config loaded **once** into `cachedConfig`. Always deploys exactly 1 AppVerticle + 1 WorkerVerticle.

## Key Design Decisions
- **K8s scaling**: 1 pod = 1 JVM = 1 AppVerticle + 1 WorkerVerticle. Do NOT add multi-instance verticle deployment.
- **Config parsed once**: `ConfigProvider.createConfig()` called in `StartupApp` and each verticle's `start()`. `AppModule` accepts pre-loaded config — does NOT call `ConfigProvider.createConfig()` internally.
- **No service interfaces**: `UserService`/`ProductService` are concrete classes (future improvement when real DB is wired).
- **Stub data**: `UserService` has hardcoded users 1–3; `ProductRepositoryImpl` serves Widget/Gadget/Doohickey from in-memory map. Replace for production.
- **Demo auth**: `AuthHandler` checks Bearer token presence only. Must be replaced with real JWT before production.

## Tech Stack
- Java, Vert.x 4.5.14, SmallRye Mutiny 2.6.2, Google Guice 7.0.0
- SmallRye Config 3.13.4, Micrometer 1.12.12, OpenTelemetry 1.32.0, Logback 1.5.13
- Gradle 9.0, Spotless + Palantir Java Format, JUnit 5 + vertx-junit5, JaCoCo

## Key Entry Points
- `StartupApp` — `com.github.kaivu.vertxweb.StartupApp`
- HTTP routes — `web/routes/RouterConfig.java`
- DI bindings — `config/AppModule.java`
- Config interface — `config/ApplicationConfig.java`
- Config file — `src/main/resources/application.yml`

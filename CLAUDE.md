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
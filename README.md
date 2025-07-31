# Vertx-web

![Vert.x 4.5.10](https://img.shields.io/badge/vert.x-4.5.10-purple.svg)

This application was generated using [start.vertx.io](http://start.vertx.io)

## Build, Lint, and Test

- **Build:** `./gradlew build`
- **Lint/Format:** `./gradlew spotlessCheck` (check), `./gradlew spotlessApply` (auto-format)
- **Test (all):** `./gradlew test`
- **Test (single):** `./gradlew test --tests ClassName.methodName`
- **Run app:** `./gradlew run`

## Project Structure & Architecture

- **Entrypoint:** `AppVerticle.java` (main verticle), `WorkerVerticle.java` (background tasks)
- **Dependency Injection:** Guice (`AppModule.java`)
- **HTTP Routing:** `RouterConfig.java` (middleware, error handling, subrouters)
- **REST Routers:** `web.rests.*Router.java` (User, Product, Common)
- **Services:** `services/` (business logic, async via Mutiny)
- **Middleware:** `middlewares/` (auth, logging)
- **Error Handling:** `web.errors.ErrorHandler.java`
- **Tests:** `src/test/java/com/github/kaivu/vertx_web/`
- **No database configured by default** (stubbed data in services)

## Build & Project Setup

- **Build Tool:** Gradle 9.0
- **Configuration:** `gradle.properties` for project properties
- **Repositories:** Ensure `repositories` block is present in `build.gradle` for dependencies
- **Main Class:** Use `application { mainClass = ... }` in `build.gradle` (not `mainClassName`)

## Code Style & Conventions

- **Formatting:** Enforced by Spotless (Palantir Java Format)
- **Imports:** Use explicit imports, avoid wildcards
- **Types:** Prefer explicit types, use Lombok for boilerplate
- **Naming:** Classes: PascalCase, methods/fields: camelCase, constants: UPPER_SNAKE_CASE
- **Error Handling:** Use Vert.x async error handling (`ctx.fail`, `failureHandler`)
- **Testing:** JUnit 5, Vert.x JUnit5 extension
- **Dependency Injection:** Use Guice `@Inject`, `@Singleton`, and `@Provides`
- **REST:** Use Vert.x Router, subrouters for modular endpoints
- **File uploads:** Enabled via BodyHandler, uploads to `uploads/`

## Help

- [Vert.x Documentation](https://vertx.io/docs/)
- [Vert.x Stack Overflow](https://stackoverflow.com/questions/tagged/vert.x?sort=newest&pageSize=15)
- [Vert.x User Group](https://groups.google.com/forum/?fromgroups#!forum/vertx)
- [Vert.x Discord](https://discord.gg/6ry7aqPWXy)

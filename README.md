# Vertx Web

![Vert.x 4.5.14](https://img.shields.io/badge/vert.x-4.5.14-purple.svg)

Reactive Vert.x + Guice demo service with a dual-verticle architecture:
- `AppVerticle` handles HTTP traffic
- `WorkerVerticle` handles EventBus consumers

The real entrypoint is `StartupApp`.

## Build, Run, Validate

- Build: `./gradlew build`
- Format check: `./gradlew spotlessCheck`
- Format apply: `./gradlew spotlessApply`
- Run: `./gradlew run`
- Validate: `./gradlew clean test check`

Note: test dependencies are configured, but there are currently no test source files (`test NO-SOURCE`).

## Current Architecture

- Bootstrap: `src/main/java/com/github/kaivu/vertxweb/StartupApp.java`
- DI module: `src/main/java/com/github/kaivu/vertxweb/config/AppModule.java`
- Main router: `src/main/java/com/github/kaivu/vertxweb/web/routes/RouterConfig.java`
- Domain routers:
  - `src/main/java/com/github/kaivu/vertxweb/web/rests/CommonRouter.java`
  - `src/main/java/com/github/kaivu/vertxweb/web/rests/UserRouter.java`
  - `src/main/java/com/github/kaivu/vertxweb/web/rests/ProductRouter.java`
  - `src/main/java/com/github/kaivu/vertxweb/web/rests/HealthRouter.java`
- Services: `src/main/java/com/github/kaivu/vertxweb/services/`
- Consumers: `src/main/java/com/github/kaivu/vertxweb/consumers/`
- Resilience pattern: `src/main/java/com/github/kaivu/vertxweb/patterns/`

## HTTP Pipeline (Current)

Global handler order in `RouterConfig`:
1. `TimeoutHandler`
2. Global `CorsHandler`
3. `LoggingHandler`
4. `AuthHandler`
5. Route handlers
6. Global `ErrorHandler` (failure handler)

Important behavior:
- CORS is now global for all routes.
- `OPTIONS` requests bypass auth for preflight support.
- Auth is demo-only by design (Bearer-token presence check only, no JWT verification).

## API Routes

- Public:
  - `GET /api/common`
  - `GET /health`
  - `GET /health/readiness`
  - `GET /health/liveness`
  - `GET /health/detailed`
- Protected:
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

## Configuration

Config is loaded via SmallRye `@ConfigMapping(prefix = "app")` from:
- `src/main/resources/application.yml`
- env var overrides (for example: `APP_SERVER_PORT`)

Current CORS config shape:

```yaml
app:
  server:
    cors:
      enable: true
      allowed-origins: "*"
      allow-credentials: false
```

YAML-source presence is explicitly validated at startup by `ConfigProvider`.

## EventBus Addresses

- `app.worker.analytics-report`
- `app.worker.batch-operation`
- `app.health.check`
- `app.worker.operation` (legacy)

## References

- [Vert.x Docs](https://vertx.io/docs/)

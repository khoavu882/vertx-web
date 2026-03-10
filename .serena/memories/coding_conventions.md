# Coding Conventions

## Style & Formatting
- **Formatter:** Palantir Java Format (enforced via Spotless — run `./gradlew spotlessApply` before committing)
- **Indentation:** 4 spaces (standard Java)
- **Naming:** UpperCamelCase for classes/interfaces, lowerCamelCase for methods/fields, UPPER_SNAKE_CASE for constants

## Import Rules
- **NEVER use wildcard imports** for `com.github.kaivu.vertxweb.constants.*`
- Always use explicit imports per constant class (e.g., `import com.github.kaivu.vertxweb.constants.HttpStatusCodes`)

## Architecture Patterns

### Reactive First
- Use `Mutiny Uni<T>` for all asynchronous operations. Never block the event loop.
- Reactive chain pattern: `.onItem().transform(...)`, `.onFailure().transform(...)`, `.replaceWithVoid()`

### Dependency Injection
- Use `@Inject` (javax.inject) with Guice. Bindings in `AppModule.java`.
- Constructor injection only — no field/setter injection.
- New classes needing DI: add `@Inject` constructor, bind in `AppModule.configure()`.

### Routing (always use handleAsync)
```java
router.get("/path").handler(ctx -> RouterHelper.handleAsync(ctx, this::myHandler));

private Uni<Void> myHandler(RoutingContext ctx) {
    return myService.doSomethingWithContext(ctx)
        .onItem().invoke(result -> RouterHelper.sendJsonResponse(ctx, HttpStatusCodes.OK, result))
        .replaceWithVoid();
}
```
All routers expose `getRouter()` (sub-router pattern). Never add routes directly to the main router from domain routers.

### Service Dual-Method Pattern
Every service public method has two forms:
- `method(args)` — no context (delegates to `methodWithContext(args, null)`)
- `methodWithContext(args, RoutingContext ctx)` — extracts `ContextAwareVertxWrapper` from ctx for correlation

### Context Propagation
- Always use `ContextAwareVertxWrapper` / `CorrelationContext` for cross-boundary tracking
- Extract wrapper: `ContextAwareVertxWrapper wrapper = ctx.get(ContextKeys.ROUTING_CONTEXT_WRAPPER)`
- EventBus: call `wrapper.enrichEventBusMessage(message)` before send; `ContextAwareVertxWrapper.fromEventBusMessage(vertx, message)` on consumer side

### Constants
- HTTP status codes: `HttpStatusCodes.*`
- Error messages: `MessageConstants.*`
- Context keys: `ContextKeys.*`
- Paths: `PathConstants.*`
- EventBus addresses: `EventBusConstants.*`
- **Never hardcode** status integers, message strings, or path strings inline

### Error Handling
- Throw `ServiceException(message, statusCode)` from services and routers
- `ErrorHandler` middleware converts `ServiceException` → JSON error response automatically
- Use `Uni.createFrom().failure(new ServiceException(...))` for reactive failure paths
- Do NOT catch exceptions and swallow them — let them propagate to `ErrorHandler`

### Circuit Breaker Usage
```java
return circuitBreakerRegistry.getDatabaseCircuitBreaker()
    .execute(() -> performMyOperation());
```
Only 5xx `ServiceException` and non-circuit-breaker exceptions count as failures. 4xx does NOT trip the breaker.

## Deployment Rules
- **1 pod = 1 AppVerticle + 1 WorkerVerticle** — never configure `setInstances(n > 1)`
- K8s manages horizontal scaling — do not add internal instance multiplying
- `ConfigProvider.createConfig()` — call only in verticle `start()` or `StartupApp.main()`. Never inside `AppModule`.

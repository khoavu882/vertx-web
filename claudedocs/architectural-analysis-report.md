# Architectural Analysis Report (Current)

**Project:** Vert.x Web Application  
**Last Verified:** February 2026  
**Scope:** Current implementation after latest reliability/config updates

---

## Executive Summary

The project uses a strong dual-verticle design and keeps HTTP I/O isolated from worker-side async processing.  
Recent fixes closed key architectural gaps:

- Global CORS now applies to all routes
- `OPTIONS` preflight now bypasses auth
- Circuit breaker now ignores non-server failures (`ServiceException <500`)
- Nested DB-breaker wrapping in `UserService` update/delete path was removed
- Vert.x timing unit handling was hardened with explicit `TimeUnit.MILLISECONDS`

Overall architecture status: **Good foundation for long-term extension**, with testing coverage still the largest gap.

---

## Current Architecture

### 1. Runtime Topology

- `StartupApp` bootstraps `Vertx`, DI wiring, and graceful shutdown
- `AppVerticle` serves HTTP routes
- `WorkerVerticle` hosts EventBus consumers

Pattern quality: **Good**

### 2. Dependency Injection

- Guice module centralizes bindings in `AppModule`
- Singleton scopes are used for routers/services/middleware/consumers
- `Router` is provided through `@Provides`

Pattern quality: **Good**

### 3. HTTP Pipeline

Current global middleware order in `RouterConfig`:
1. Timeout
2. CORS
3. Request logging
4. Auth
5. Route handlers
6. Error handler (failure path)

Key improvement now in place:
- CORS is no longer subrouter-local; it is applied globally.

Pattern quality: **Good**

### 4. Service and Async Model

- Services use Mutiny `Uni`
- Request lifecycle context is propagated through `ContextAwareVertxWrapper`
- EventBus integration is used for analytics/batch worker flows

Pattern quality: **Good**

### 5. Resilience

- Custom circuit breaker with CLOSED/OPEN/HALF_OPEN states
- Registry-based breaker management
- Failure classification now treats business/client failures as non-countable

Pattern quality: **Improved**

### 6. Configuration

- SmallRye typed mapping via `@ConfigMapping(prefix = "app")`
- YAML source presence is validated at startup
- CORS config is now nested object form:

```yaml
app:
  server:
    cors:
      enable: true
      allowed-origins: "*"
      allow-credentials: false
```

Pattern quality: **Good**

---

## Verified Strengths

- Clear separation of HTTP and worker concerns
- Clean DI composition and modular routing
- Correlation context propagation across HTTP/EventBus boundaries
- Config-driven behavior with typed mapping
- Improved resilience behavior through breaker failure classification

---

## Remaining Risks and Gaps

### 1. Test Coverage Gap (High Priority)

- `src/test/java` currently has no test sources (`NO-SOURCE`)
- Any refactor can regress behavior without safety net

Recommendation:
- Add minimum smoke tests for:
  - route registration and pipeline behavior
  - auth bypass for preflight/public routes
  - circuit-breaker classification logic

### 2. Demo Auth Model (Known/Accepted for Demo)

- Auth is bearer-presence only, no JWT validation
- This is explicitly acceptable for this demo project, but not production safe

### 3. ProductService Data Model Consistency

- Product read paths and create/update return payloads use partially different field shapes
- For long-term API stability, formalize response schema contracts

---

## Practical Next Steps

1. Add baseline automated tests (router/auth/circuit-breaker behavior)
2. Introduce API response schema contract tests for users/products
3. Add lightweight observability metrics (breaker state + request latency buckets)

---

## Conclusion

The project architecture is solid and now materially safer after the latest CORS, breaker, and timing fixes.  
The primary long-term blocker is missing tests, not design structure.

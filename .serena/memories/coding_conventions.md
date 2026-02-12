# Coding Conventions

## Style & Formatting

- **Formatter:** Palantir Java Format (enforced via Spotless).
- **Indentation:** 4 spaces (standard Java).
- **Naming:** CamelCase for classes/methods, CONSTANT_CASE for constants.

## Architecture Patterns

- **Reactive First:** Use `Mutiny Uni<T>` for all asynchronous operations. Avoid callbacks where possible.
- **Dependency Injection:** Use `@Inject` (javax.inject) with Guice. Bindings are in `AppModule.java`.
- **Context Propagation:** Always use `ContextAwareVertxWrapper` or `CorrelationContext` to pass headers/IDs.
  - Services often have two methods: `method()` (legacy/no-context) and `methodWithContext(..., ctx)`.
- **Routing:**
  - Domain-specific routers (e.g., `UserRouter`) should be sub-routers mounted in `RouterConfig`.
  - Use `RouterHelper.handleAsync` for consistent error handling and context management.
- **Validation:** Use composable `ValidationRule`s in `Validator` class.

## Error Handling

- Use `AppConstants.Status` for HTTP codes.
- Use `AppConstants.Messages` for error messages.
- Failures should return `Uni.createFrom().failure(...)` or propagate exceptions to `ErrorHandler`.

# Suggested Commands

## Build & Test
```bash
./gradlew build                              # Full build (compile + test + spotless check)
./gradlew test                               # Run all tests
./gradlew test --tests ClassName.methodName  # Run single test
./gradlew clean test check                   # Full validation: tests + JaCoCo coverage gate (80%)
```

## Code Quality
```bash
./gradlew spotlessCheck   # Check formatting (fails if not formatted)
./gradlew spotlessApply   # Auto-format all Java files (run before committing)
```

## Run Application
```bash
./gradlew run             # Run via Gradle application plugin
```

Application starts on port **8081** by default (set in `application.yml`).

## Environment Variable Overrides (K8s / Docker)
```bash
APP_SERVER_PORT=8080
APP_SECURITY_JWT_SECRET=<real-secret>
APP_OBSERVABILITY_TRACING_EXPORTER=otlp
APP_OBSERVABILITY_TRACING_OTLP_ENDPOINT=http://otel-collector:4317
APP_OBSERVABILITY_METRICS_EXPOSURE=open
```

## JaCoCo Coverage Report
```bash
./gradlew jacocoTestReport   # Generate HTML report in build/reports/jacoco/test/html/
```

## Miscellaneous
```bash
./gradlew clean                              # Clean build outputs
./gradlew build --refresh-dependencies       # Refresh all dependencies
./gradlew dependencies                       # Print dependency tree
```

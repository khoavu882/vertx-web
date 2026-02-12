# Tech Stack

## Core

- **Language:** Java (implied 17+)
- **Build System:** Gradle 9.0
- **Main Framework:** Eclipse Vert.x 4.5.14
  - `vertx-core`
  - `vertx-web`
- **Dependency Injection:** Google Guice 7.0.0

## Reactive & Async

- **Library:** SmallRye Mutiny 2.6.2
- **Patterns:** Unis and Multis, avoiding callback hell.

## Configuration

- **Library:** SmallRye Config 3.13.4
- **Format:** YAML (`application.yml`) & Environment Variables
- **Features:** Type-safe, interface-based configuration mapping.

## Development Tools

- **Boilerplate Reduction:** Project Lombok 1.18.30 (approx, version variable in properties)
- **Code Formatting:** Spotless Plugin 6.25.0 with Palantir Java Format.

## Testing

- **Framework:** JUnit 5 (Jupiter)
- **Integration:** `vertx-junit5` extension
- **Assertions:** (Likely standard JUnit or AssertJ, TBD)

## Logging

- **Facade:** SLF4J
- **Implementation:** Logback Classic
- **Features:** MDC support for request correlation.

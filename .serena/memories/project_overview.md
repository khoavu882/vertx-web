# Project Overview

**Project:** Vert.x Web Application
**Purpose:** A high-performance, reactive web application using Vert.x.
**Architecture:**

- **Dual Verticle System:**
  - `AppVerticle`: Handles HTTP requests, scales with CPU cores.
  - `WorkerVerticle`: Handles background processing and blocking tasks.
- **Event-Driven:** Uses Vert.x EventBus for communication between verticles.
- **Dependency Injection:** Google Guice 7.0.0 for clean code structure.
- **Configuration:** SmallRye Config for type-safe, environment-aware configuration.

**Tech Stack:**

- **Language:** Java
- **Core Framework:** Vert.x 4.5.14
- **Reactive Library:** Mutiny 2.6.2 (Uni/Multi)
- **Dependency Injection:** Google Guice 7.0.0
- **Build System:** Gradle 9.0
- **Logging:** SLF4J with MDC and Correlation ID tracking

**Key Components:**

- `StartupApp`: Entry point (`com.github.kaivu.vertxweb.StartupApp`).
- `RouterConfig`: Centralized HTTP routing.
- `ContextAwareVertxWrapper`: Manages request context and correlation IDs.

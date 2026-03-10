package com.github.kaivu.vertxweb.web;

import io.vertx.ext.web.Router;

/**
 * Marker interface for Guice Multibinder auto-registration of domain HTTP routers.
 *
 * <p>Implement this on every domain router that should be mounted in RouterConfig. New routers
 * require only one Multibinder binding in AppModule — RouterConfig itself never needs to change.
 *
 * <p>Infrastructure routers (health, metrics, openapi) are injected individually in RouterConfig
 * because they have special mounting semantics (public paths, no auth).
 */
public interface AppRouter {

    /** Returns the sub-router to mount. */
    Router getRouter();

    /**
     * Returns the mount path pattern, e.g. {@code "/api/orders/*"}.
     *
     * <p>Include the full path with trailing {@code /*} so Vert.x routes all sub-paths correctly.
     */
    String getMountPath();

    /**
     * Whether this router's routes require authentication.
     *
     * <p>Protected routers are mounted after the auth middleware. Public routers are mounted before
     * it. Defaults to {@code true}.
     */
    default boolean isProtected() {
        return true;
    }
}

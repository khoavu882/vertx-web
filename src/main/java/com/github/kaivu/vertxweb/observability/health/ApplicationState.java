package com.github.kaivu.vertxweb.observability.health;

import com.google.inject.Singleton;

@Singleton
public class ApplicationState {
    private final long startedAtMs = System.currentTimeMillis();

    public long uptimeMs() {
        return System.currentTimeMillis() - startedAtMs;
    }
}

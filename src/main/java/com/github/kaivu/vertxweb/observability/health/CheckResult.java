package com.github.kaivu.vertxweb.observability.health;

import java.util.Map;

public record CheckResult(String name, CheckType type, CheckStatus status, Map<String, Object> data, String message) {
    public static CheckResult up(String name, CheckType type, Map<String, Object> data) {
        return new CheckResult(name, type, CheckStatus.UP, data == null ? Map.of() : Map.copyOf(data), null);
    }

    public static CheckResult down(String name, CheckType type, String message, Map<String, Object> data) {
        return new CheckResult(name, type, CheckStatus.DOWN, data == null ? Map.of() : Map.copyOf(data), message);
    }
}

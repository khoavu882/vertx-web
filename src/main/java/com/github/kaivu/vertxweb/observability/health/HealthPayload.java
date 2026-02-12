package com.github.kaivu.vertxweb.observability.health;

import java.util.List;

public record HealthPayload(String status, List<CheckResult> checks, long generatedAtMs) {
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    public static HealthPayload fromChecks(List<CheckResult> checks) {
        List<CheckResult> safeChecks = checks == null ? List.of() : List.copyOf(checks);
        boolean hasDown = safeChecks.stream().anyMatch(check -> check.status() == CheckStatus.DOWN);
        return new HealthPayload(hasDown ? DOWN : UP, safeChecks, System.currentTimeMillis());
    }
}

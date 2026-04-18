package com.routechain.v2.ops;

import java.util.List;
import java.util.Set;

public final class DispatchOpsStatusMapper {
    private static final Set<String> TOMTOM_RISK_REASONS = Set.of(
            "tomtom-auth-or-quota-failed",
            "tomtom-http-error",
            "tomtom-timeout",
            "tomtom-unavailable",
            "tomtom-unavailable-or-no-data");
    private static final Set<String> OPEN_METEO_FALLBACK_REASONS = Set.of(
            "open-meteo-http-error",
            "open-meteo-malformed-payload",
            "open-meteo-timeout",
            "open-meteo-interrupted",
            "open-meteo-unavailable",
            "open-meteo-stale");

    private DispatchOpsStatusMapper() {
    }

    public static String tomTomStatus(boolean enabled, boolean apiKeyPresent, List<String> degradeReasons) {
        if (!enabled) {
            return "disabled";
        }
        if (!apiKeyPresent) {
            return "missing-api-key";
        }
        List<String> reasons = degradeReasons == null ? List.of() : degradeReasons;
        return reasons.stream().anyMatch(TOMTOM_RISK_REASONS::contains) ? "auth-or-quota-risk" : "ok";
    }

    public static String openMeteoStatus(boolean enabled, List<String> degradeReasons) {
        if (!enabled) {
            return "disabled";
        }
        List<String> reasons = degradeReasons == null ? List.of() : degradeReasons;
        return reasons.stream().anyMatch(OPEN_METEO_FALLBACK_REASONS::contains) ? "fallback-only" : "ok";
    }
}

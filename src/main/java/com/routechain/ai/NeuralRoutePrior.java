package com.routechain.ai;

import java.time.Instant;
import java.util.List;

/**
 * Nearline neural route prior used as a soft signal in dispatch scoring.
 */
public record NeuralRoutePrior(
        String zoneId,
        double priorScore,
        List<String> routeTemplateIds,
        double confidence,
        long freshnessMs,
        String modelVersion,
        boolean used,
        boolean fallbackUsed,
        String fallbackReason,
        String modelFamily,
        String backend,
        long latencyMs,
        Instant asOf
) {
    public NeuralRoutePrior {
        zoneId = zoneId == null || zoneId.isBlank() ? "unknown-zone" : zoneId;
        routeTemplateIds = routeTemplateIds == null ? List.of() : List.copyOf(routeTemplateIds);
        modelVersion = modelVersion == null || modelVersion.isBlank() ? "neural-prior-fallback-v1" : modelVersion;
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? "none" : fallbackReason;
        modelFamily = modelFamily == null || modelFamily.isBlank() ? "neural-route-prior" : modelFamily;
        backend = backend == null || backend.isBlank() ? "fallback" : backend;
        freshnessMs = Math.max(0L, freshnessMs);
        latencyMs = Math.max(0L, latencyMs);
        asOf = asOf == null ? Instant.now() : asOf;
    }

    public static NeuralRoutePrior fallback(String zoneId, String fallbackReason) {
        return new NeuralRoutePrior(
                zoneId,
                0.0,
                List.of(),
                0.0,
                0L,
                "neural-prior-fallback-v1",
                false,
                true,
                fallbackReason,
                "neural-route-prior",
                "fallback",
                0L,
                Instant.now());
    }
}

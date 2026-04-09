package com.routechain.graph;

/**
 * Difference between the open-source routing baseline and live observed
 * operational conditions.
 */
public record TravelTimeDriftSnapshot(
        String corridorId,
        double baselineTravelMinutes,
        double liveTravelMinutes,
        double driftRatio,
        double confidence
) {
    public TravelTimeDriftSnapshot {
        corridorId = corridorId == null || corridorId.isBlank() ? "corridor-unknown" : corridorId;
        baselineTravelMinutes = Math.max(0.0, baselineTravelMinutes);
        liveTravelMinutes = Math.max(0.0, liveTravelMinutes);
        driftRatio = Math.max(0.0, Math.min(1.5, driftRatio));
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
}

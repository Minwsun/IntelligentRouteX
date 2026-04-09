package com.routechain.graph;

/**
 * Live corridor state derived from the open-source road graph baseline and
 * runtime telemetry.
 */
public record CorridorLiveState(
        String corridorId,
        String fromCellId,
        String toCellId,
        double baselineDistanceKm,
        double baselineTravelMinutes,
        double topologyStretchFactor,
        double congestionScore,
        double weatherSeverity,
        double confidence
) {
    public CorridorLiveState {
        corridorId = corridorId == null || corridorId.isBlank() ? "corridor-unknown" : corridorId;
        fromCellId = fromCellId == null || fromCellId.isBlank() ? "cell-unknown" : fromCellId;
        toCellId = toCellId == null || toCellId.isBlank() ? "cell-unknown" : toCellId;
        baselineDistanceKm = Math.max(0.0, baselineDistanceKm);
        baselineTravelMinutes = Math.max(0.0, baselineTravelMinutes);
        topologyStretchFactor = clamp(topologyStretchFactor, 1.0, 2.5);
        congestionScore = clamp01(congestionScore);
        weatherSeverity = clamp01(weatherSeverity);
        confidence = clamp01(confidence);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

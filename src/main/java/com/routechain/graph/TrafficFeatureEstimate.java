package com.routechain.graph;

import java.util.Map;

/**
 * Bigdata-first traffic feature bundle derived from telemetry and the
 * open-source road graph baseline.
 */
public record TrafficFeatureEstimate(
        String traceId,
        String roadGraphBackend,
        String pickupCellId,
        String dropCellId,
        String approachCorridorId,
        String deliveryCorridorId,
        double pickupFrictionScore,
        double dropReachabilityScore,
        double corridorCongestionScore,
        double zoneSlowdownIndex,
        double travelTimeDriftScore,
        double trafficUncertaintyScore,
        Map<String, Object> traceSnapshot
) {
    public TrafficFeatureEstimate {
        traceId = traceId == null || traceId.isBlank() ? "trace-unknown" : traceId;
        roadGraphBackend = roadGraphBackend == null || roadGraphBackend.isBlank()
                ? "osm-osrm-surrogate-v1"
                : roadGraphBackend;
        pickupCellId = pickupCellId == null || pickupCellId.isBlank() ? "cell-unknown" : pickupCellId;
        dropCellId = dropCellId == null || dropCellId.isBlank() ? "cell-unknown" : dropCellId;
        approachCorridorId = approachCorridorId == null || approachCorridorId.isBlank()
                ? "corridor-unknown"
                : approachCorridorId;
        deliveryCorridorId = deliveryCorridorId == null || deliveryCorridorId.isBlank()
                ? "corridor-unknown"
                : deliveryCorridorId;
        pickupFrictionScore = clamp01(pickupFrictionScore);
        dropReachabilityScore = clamp01(dropReachabilityScore);
        corridorCongestionScore = clamp01(corridorCongestionScore);
        zoneSlowdownIndex = clamp01(zoneSlowdownIndex);
        travelTimeDriftScore = clamp01(travelTimeDriftScore);
        trafficUncertaintyScore = clamp01(trafficUncertaintyScore);
        traceSnapshot = traceSnapshot == null ? Map.of() : Map.copyOf(traceSnapshot);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

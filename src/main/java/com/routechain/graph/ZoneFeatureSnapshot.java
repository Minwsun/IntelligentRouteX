package com.routechain.graph;

import com.routechain.domain.GeoPoint;

/**
 * Zone-level operational state used to describe pickup and landing quality.
 */
public record ZoneFeatureSnapshot(
        String zoneId,
        GeoPoint anchorPoint,
        double openDemand,
        double activeSupply,
        double trafficForecast10m,
        double weatherSeverity,
        double postDropOpportunity10m,
        double emptyZoneRisk10m,
        double slowdownIndex,
        double attractionScore,
        double committedPickupPressure
) {
    public ZoneFeatureSnapshot {
        zoneId = zoneId == null || zoneId.isBlank() ? "cell-unknown" : zoneId;
        openDemand = Math.max(0.0, openDemand);
        activeSupply = Math.max(0.0, activeSupply);
        trafficForecast10m = clamp01(trafficForecast10m);
        weatherSeverity = clamp01(weatherSeverity);
        postDropOpportunity10m = clamp01(postDropOpportunity10m);
        emptyZoneRisk10m = clamp01(emptyZoneRisk10m);
        slowdownIndex = clamp01(slowdownIndex);
        attractionScore = clamp01(attractionScore);
        committedPickupPressure = clamp01(committedPickupPressure);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

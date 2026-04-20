package com.routechain.v2.routing;

import com.routechain.v2.SchemaVersioned;

public record LegRouteVector(
        String schemaVersion,
        String fromStopId,
        String toStopId,
        double deltaLat,
        double deltaLng,
        double bearingStartDeg,
        double bearingEndDeg,
        double bearingMeanDeg,
        double distanceMeters,
        double travelTimeSeconds,
        double avgSpeedMps,
        double majorRoadRatio,
        double minorRoadRatio,
        int turnCount,
        int leftTurnCount,
        int rightTurnCount,
        int uTurnCount,
        double straightnessScore,
        double congestionScore,
        double roadRiskScore,
        double routeCost) implements SchemaVersioned {
}

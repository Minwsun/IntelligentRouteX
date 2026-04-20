package com.routechain.v2.routing;

import com.routechain.v2.SchemaVersioned;

public record RouteVectorSummary(
        String schemaVersion,
        String proposalId,
        int legCount,
        double totalDistanceMeters,
        double totalTravelTimeSeconds,
        double avgSpeedMps,
        double majorRoadRatio,
        double minorRoadRatio,
        int turnCount,
        int uTurnCount,
        double straightnessScore,
        double corridorPreferenceScore,
        double congestionScore,
        double routeCost,
        String directionSignature,
        boolean geometryAvailable) implements SchemaVersioned {
}

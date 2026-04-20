package com.routechain.v2.routing;

public final class RouteCostFunction {

    public double score(double liveTravelTimeSeconds,
                        double congestionScore,
                        double minorRoadRatio,
                        int turnCount,
                        int uTurnCount,
                        double corridorPreferenceScore,
                        double straightnessScore,
                        double distanceMeters) {
        double longRouteBonus = distanceMeters >= 3_000.0 ? 0.08 : 0.0;
        return Math.max(0.0,
                liveTravelTimeSeconds
                        + (congestionScore * 90.0)
                        + (minorRoadRatio * 40.0)
                        + (turnCount * 4.0)
                        + (uTurnCount * 18.0)
                        - (corridorPreferenceScore * 25.0)
                        - (straightnessScore * 20.0)
                        - longRouteBonus);
    }
}

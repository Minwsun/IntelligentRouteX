package com.routechain.v2.routing;

public final class SyntheticRoadGraphProvider implements RoadGraphProvider {

    @Override
    public RoadProfile profile(RouteStop fromStop, RouteStop toStop) {
        double deltaLat = Math.abs(toStop.latitude() - fromStop.latitude());
        double deltaLng = Math.abs(toStop.longitude() - fromStop.longitude());
        double corridorBias = Math.min(1.0, (deltaLat + deltaLng) * 60.0);
        double majorRoadRatio = clamp(0.45 + corridorBias * 0.35);
        double minorRoadRatio = clamp(1.0 - majorRoadRatio);
        int turnCount = Math.max(1, (int) Math.round((deltaLat + deltaLng) * 140.0));
        int uTurnCount = turnCount >= 6 ? 1 : 0;
        int leftTurnCount = turnCount / 2;
        int rightTurnCount = Math.max(0, turnCount - leftTurnCount - uTurnCount);
        double congestionScore = clamp(0.18 + (deltaLat * 22.0) + (deltaLng * 18.0));
        return new RoadProfile(
                majorRoadRatio,
                minorRoadRatio,
                congestionScore,
                turnCount,
                leftTurnCount,
                rightTurnCount,
                uTurnCount,
                clamp(majorRoadRatio - congestionScore * 0.25));
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

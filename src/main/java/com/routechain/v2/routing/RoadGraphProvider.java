package com.routechain.v2.routing;

public interface RoadGraphProvider {
    RoadProfile profile(RouteStop fromStop, RouteStop toStop);

    record RoadProfile(
            double majorRoadRatio,
            double minorRoadRatio,
            double congestionScore,
            int turnCount,
            int leftTurnCount,
            int rightTurnCount,
            int uTurnCount,
            double corridorPreferenceScore) {
    }
}

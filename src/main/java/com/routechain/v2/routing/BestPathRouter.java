package com.routechain.v2.routing;

public final class BestPathRouter {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final RoadGraphProvider roadGraphProvider;
    private final RouteCostFunction routeCostFunction;

    public BestPathRouter(RoadGraphProvider roadGraphProvider, RouteCostFunction routeCostFunction) {
        this.roadGraphProvider = roadGraphProvider;
        this.routeCostFunction = routeCostFunction;
    }

    public BestPathResult route(BestPathRequest request) {
        RouteStop fromStop = request.fromStop();
        RouteStop toStop = request.toStop();
        double distanceMeters = haversineMeters(fromStop.latitude(), fromStop.longitude(), toStop.latitude(), toStop.longitude());
        RoadGraphProvider.RoadProfile profile = roadGraphProvider.profile(fromStop, toStop);
        double baseSpeedMps = 7.2 + (profile.majorRoadRatio() * 4.0) - (profile.congestionScore() * 2.5);
        double avgSpeedMps = Math.max(3.2, baseSpeedMps);
        double travelTimeSeconds = distanceMeters / avgSpeedMps;
        double straightnessScore = clamp(1.0 - ((profile.turnCount() * 0.06) + (profile.uTurnCount() * 0.18)));
        double routeCost = routeCostFunction.score(
                travelTimeSeconds,
                profile.congestionScore(),
                profile.minorRoadRatio(),
                profile.turnCount(),
                profile.uTurnCount(),
                profile.corridorPreferenceScore(),
                straightnessScore,
                distanceMeters);
        double bearing = bearingDegrees(fromStop.latitude(), fromStop.longitude(), toStop.latitude(), toStop.longitude());
        LegRouteVector legVector = new LegRouteVector(
                "route-leg-vector/v1",
                fromStop.stopId(),
                toStop.stopId(),
                toStop.latitude() - fromStop.latitude(),
                toStop.longitude() - fromStop.longitude(),
                bearing,
                bearing,
                bearing,
                distanceMeters,
                travelTimeSeconds,
                avgSpeedMps,
                profile.majorRoadRatio(),
                profile.minorRoadRatio(),
                profile.turnCount(),
                profile.leftTurnCount(),
                profile.rightTurnCount(),
                profile.uTurnCount(),
                straightnessScore,
                profile.congestionScore(),
                clamp((profile.minorRoadRatio() * 0.55) + (profile.uTurnCount() * 0.15)),
                routeCost);
        return new BestPathResult(legVector, profile.corridorPreferenceScore());
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double y = Math.sin(Math.toRadians(lon2 - lon1)) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2 - lon1));
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

package com.routechain.v2.routing;

public record BestPathResult(
        LegRouteVector legVector,
        double corridorPreferenceScore) {
}

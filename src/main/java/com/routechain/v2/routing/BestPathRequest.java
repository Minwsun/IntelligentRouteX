package com.routechain.v2.routing;

public record BestPathRequest(
        RouteStop fromStop,
        RouteStop toStop,
        String trafficProfile,
        String weatherClass,
        int timeBucketMinutes) {
}

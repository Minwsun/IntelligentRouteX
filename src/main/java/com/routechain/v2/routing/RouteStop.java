package com.routechain.v2.routing;

public record RouteStop(
        String stopId,
        double latitude,
        double longitude,
        String stopType,
        String zoneId,
        Double readyEtaMinutes) {
}

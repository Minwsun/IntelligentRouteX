package com.routechain.v2.integration;

public record TomTomTrafficRefineRequest(
        String schemaVersion,
        String traceId,
        double fromLatitude,
        double fromLongitude,
        double toLatitude,
        double toLongitude,
        long decisionEpochMillis,
        double baselineMinutes,
        double distanceKm) {
}

package com.routechain.v2;

public record BundleScore(
        double pickupCompactness,
        double dropCoherence,
        double lowEtaIncrease,
        double slaSafety,
        double lowZigzag,
        double landingValue,
        double trafficWeatherResilience,
        double totalScore) {
}

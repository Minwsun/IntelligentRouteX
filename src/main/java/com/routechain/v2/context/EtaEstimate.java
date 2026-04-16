package com.routechain.v2.context;

public record EtaEstimate(
        double etaMinutes,
        double etaUncertainty,
        String corridorId,
        TrafficState trafficState,
        WeatherState weatherState,
        double trafficMultiplier,
        double weatherMultiplier,
        double refineMultiplier,
        double corridorCongestionScore,
        double travelTimeDrift) {
}

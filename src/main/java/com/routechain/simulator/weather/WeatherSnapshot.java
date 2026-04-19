package com.routechain.simulator.weather;

import com.routechain.domain.WeatherProfile;

import java.time.Instant;

public record WeatherSnapshot(
        Instant observedAt,
        WeatherProfile profile,
        double demandMultiplier,
        double driverSpeedMultiplier,
        double trafficPenaltyMultiplier,
        double merchantPrepPenaltyMultiplier,
        String regime) {
}

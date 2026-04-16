package com.routechain.v2.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaUncertaintyEstimatorTest {

    @Test
    void freshInputsProduceLowerUncertainty() {
        EtaUncertaintyEstimator estimator = new EtaUncertaintyEstimator();
        double fresh = estimator.estimate(
                new TrafficProfileSnapshot("traffic-profile-snapshot/v1", 1.0, TrafficProfileSource.PROFILE_DEFAULT, 0L, 0.95, false),
                new WeatherContextSnapshot("weather-context-snapshot/v1", 1.0, false, WeatherSource.REQUEST_PROFILE, 0L, 1.0),
                true,
                true,
                new FreshnessMetadata("freshness-metadata/v1", 0L, 0L, 0L, true, true, true));
        double stale = estimator.estimate(
                new TrafficProfileSnapshot("traffic-profile-snapshot/v1", 1.0, TrafficProfileSource.DEGRADED_PROFILE, 1000L, 0.4, false),
                new WeatherContextSnapshot("weather-context-snapshot/v1", 1.0, false, WeatherSource.DEGRADED_PROFILE, 1000L, 0.3),
                false,
                false,
                new FreshnessMetadata("freshness-metadata/v1", 1000L, 1000L, 1000L, false, false, false));
        assertTrue(stale > fresh);
    }
}

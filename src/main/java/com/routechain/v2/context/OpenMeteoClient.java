package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;

/**
 * Placeholder client. The first cutover slice keeps runtime-safe heuristics and
 * only exposes an integration seam for Open-Meteo.
 */
public final class OpenMeteoClient {
    private final RouteChainDispatchV2Properties.OpenMeteo properties;

    public OpenMeteoClient(RouteChainDispatchV2Properties.OpenMeteo properties) {
        this.properties = properties;
    }

    public WeatherContext lookup(GeoPoint point, WeatherProfile fallbackProfile) {
        WeatherState state = switch (fallbackProfile) {
            case CLEAR -> WeatherState.CLEAR;
            case LIGHT_RAIN -> WeatherState.LIGHT_RAIN;
            case HEAVY_RAIN -> WeatherState.HEAVY_RAIN;
            case STORM -> WeatherState.STORM;
        };
        double rainProbability = switch (state) {
            case CLEAR -> 0.10;
            case LIGHT_RAIN -> 0.42;
            case MODERATE_RAIN -> 0.58;
            case HEAVY_RAIN -> 0.72;
            case STORM -> 0.88;
        };
        double rainIntensity = switch (state) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 1.4;
            case MODERATE_RAIN -> 2.7;
            case HEAVY_RAIN -> 5.5;
            case STORM -> 7.5;
        };
        double visibility = switch (state) {
            case CLEAR -> 10_000.0;
            case LIGHT_RAIN -> 6_500.0;
            case MODERATE_RAIN -> 4_800.0;
            case HEAVY_RAIN -> 3_500.0;
            case STORM -> 2_500.0;
        };
        double windSpeed = switch (state) {
            case CLEAR -> 2.5;
            case LIGHT_RAIN -> 5.0;
            case MODERATE_RAIN -> 7.2;
            case HEAVY_RAIN -> 10.9;
            case STORM -> 14.8;
        };
        boolean thunder = state == WeatherState.STORM;
        boolean badSignal = rainProbability >= 0.35
                || rainIntensity >= 2.5
                || thunder
                || visibility <= 4_000.0
                || windSpeed >= 10.8;
        return new WeatherContext(state, rainProbability, rainIntensity, visibility, windSpeed, thunder, badSignal);
    }

    public boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }
}

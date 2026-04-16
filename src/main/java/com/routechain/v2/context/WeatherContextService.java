package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.integration.OpenMeteoClient;
import com.routechain.v2.integration.OpenMeteoSnapshot;

public final class WeatherContextService {
    private final RouteChainDispatchV2Properties properties;
    private final OpenMeteoClient openMeteoClient;

    public WeatherContextService(RouteChainDispatchV2Properties properties, OpenMeteoClient openMeteoClient) {
        this.properties = properties;
        this.openMeteoClient = openMeteoClient;
    }

    public WeatherContextSnapshot resolveWeather(DispatchV2Request request, GeoPoint point) {
        WeatherProfile profile = request == null || request.weatherProfile() == null
                ? WeatherProfile.CLEAR
                : request.weatherProfile();
        if (point == null) {
            return new WeatherContextSnapshot(
                    "weather-context-snapshot/v1",
                    1.0,
                    false,
                    WeatherSource.DEGRADED_PROFILE,
                    properties.getContext().getFreshness().getWeatherMaxAge().toMillis() + 1,
                    0.2);
        }

        if (properties.isOpenMeteoEnabled()) {
            OpenMeteoSnapshot snapshot = openMeteoClient.fetchForecast(point, request == null ? null : request.decisionTime());
            if (snapshot.available()) {
                boolean stale = snapshot.sourceAgeMs() > properties.getContext().getFreshness().getWeatherMaxAge().toMillis();
                double confidence = stale ? Math.min(snapshot.confidence(), 0.35) : snapshot.confidence();
                return new WeatherContextSnapshot(
                        "weather-context-snapshot/v1",
                        profileMultiplier(profile),
                        profile == WeatherProfile.HEAVY_RAIN && confidence >= 0.25,
                        stale ? WeatherSource.DEGRADED_PROFILE : WeatherSource.OPEN_METEO,
                        snapshot.sourceAgeMs(),
                        confidence);
            }
        }

        return new WeatherContextSnapshot(
                "weather-context-snapshot/v1",
                profileMultiplier(profile),
                profile == WeatherProfile.HEAVY_RAIN,
                WeatherSource.REQUEST_PROFILE,
                0L,
                1.0);
    }

    private double profileMultiplier(WeatherProfile profile) {
        return switch (profile) {
            case HEAVY_RAIN -> properties.getContext().getHeavyRainMultiplier();
            case LIGHT_RAIN -> properties.getContext().getLightRainMultiplier();
            case CLEAR -> 1.0;
        };
    }
}


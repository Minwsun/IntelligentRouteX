package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;

public final class WeatherContextService {
    private final RouteChainDispatchV2Properties properties;
    private final OpenMeteoClient openMeteoClient;

    public WeatherContextService(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
        this.openMeteoClient = new OpenMeteoClient(properties.getOpenMeteo());
    }

    public WeatherContext resolve(GeoPoint point, WeatherProfile fallbackProfile) {
        WeatherContext context = openMeteoClient.lookup(point, fallbackProfile);
        return new WeatherContext(
                normalize(context.weatherState()),
                context.rainProbability15m(),
                context.rainIntensityMmPerHour(),
                context.visibilityMeters(),
                context.windSpeed10mMetersPerSecond(),
                context.thunderOrStormAlert(),
                context.badSignal());
    }

    public double multiplier(WeatherState weatherState) {
        return normalize(weatherState).etaMultiplier();
    }

    private WeatherState normalize(WeatherState weatherState) {
        if (weatherState == null) {
            return WeatherState.CLEAR;
        }
        if (weatherState == WeatherState.MODERATE_RAIN) {
            return WeatherState.MODERATE_RAIN;
        }
        return weatherState;
    }
}

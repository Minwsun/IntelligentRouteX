package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;

public final class WeatherSignalMapper {
    private final RouteChainDispatchV2Properties properties;

    public WeatherSignalMapper(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public ResolvedWeatherSignal resolve(int weatherCode) {
        if (weatherCode >= 63) {
            return new ResolvedWeatherSignal("heavy-rain", properties.getContext().getHeavyRainMultiplier(), true);
        }
        if (weatherCode >= 51) {
            return new ResolvedWeatherSignal("light-rain", properties.getContext().getLightRainMultiplier(), false);
        }
        return new ResolvedWeatherSignal("clear", 1.0, false);
    }

    public record ResolvedWeatherSignal(
            String condition,
            double multiplier,
            boolean weatherBadSignal) {
    }
}

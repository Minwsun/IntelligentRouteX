package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.integration.TestOpenMeteoClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherContextServiceLiveIntegrationTest {

    @Test
    void freshLiveWeatherOverridesRequestProfile() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);

        WeatherContextSnapshot snapshot = new WeatherContextService(properties, TestOpenMeteoClient.freshHeavyRain())
                .resolveWeather(request(WeatherProfile.CLEAR), point());

        assertEquals(WeatherSource.OPEN_METEO, snapshot.source());
        assertEquals(1.28, snapshot.multiplier());
        assertTrue(snapshot.weatherBadSignal());
    }

    @Test
    void staleLiveWeatherFallsBackToRequestProfile() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);

        WeatherContextSnapshot snapshot = new WeatherContextService(properties,
                TestOpenMeteoClient.staleHeavyRain(properties.getContext().getFreshness().getWeatherMaxAge().toMillis() + 1))
                .resolveWeather(request(WeatherProfile.LIGHT_RAIN), point());

        assertEquals(WeatherSource.DEGRADED_PROFILE, snapshot.source());
        assertEquals(properties.getContext().getLightRainMultiplier(), snapshot.multiplier());
        assertTrue(snapshot.confidence() < 0.92);
    }

    private DispatchV2Request request(WeatherProfile weatherProfile) {
        return new DispatchV2Request("dispatch-v2-request/v1", "trace-weather-live", List.of(), List.of(), List.of(), weatherProfile, Instant.parse("2026-04-16T12:00:00Z"));
    }

    private GeoPoint point() {
        return new GeoPoint(10.770, 106.690);
    }
}

package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.OpenMeteoClient;
import com.routechain.v2.integration.OpenMeteoSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherContextServiceTest {

    @Test
    void resolvesClearWeatherFromRequestProfile() {
        WeatherContextService service = new WeatherContextService(RouteChainDispatchV2Properties.defaults(), new NoOpOpenMeteoClient());
        WeatherContextSnapshot snapshot = service.resolveWeather(request(WeatherProfile.CLEAR), point());
        assertEquals(1.0, snapshot.multiplier());
        assertEquals(WeatherSource.REQUEST_PROFILE, snapshot.source());
    }

    @Test
    void resolvesLightRainMultiplier() {
        WeatherContextService service = new WeatherContextService(RouteChainDispatchV2Properties.defaults(), new NoOpOpenMeteoClient());
        WeatherContextSnapshot snapshot = service.resolveWeather(request(WeatherProfile.LIGHT_RAIN), point());
        assertEquals(1.07, snapshot.multiplier());
        assertFalse(snapshot.weatherBadSignal());
    }

    @Test
    void resolvesHeavyRainSignal() {
        WeatherContextService service = new WeatherContextService(RouteChainDispatchV2Properties.defaults(), new NoOpOpenMeteoClient());
        WeatherContextSnapshot snapshot = service.resolveWeather(request(WeatherProfile.HEAVY_RAIN), point());
        assertEquals(1.28, snapshot.multiplier());
        assertTrue(snapshot.weatherBadSignal());
    }

    @Test
    void staleWeatherLowersConfidence() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);
        OpenMeteoClient staleClient = new OpenMeteoClient() {
            @Override
            public OpenMeteoSnapshot fetchForecast(GeoPoint point, Instant decisionTime) {
                return new OpenMeteoSnapshot(true, "heavy-rain", 1.28, true, properties.getContext().getFreshness().getWeatherMaxAge().toMillis() + 1, 0.9, 6L, "");
            }

            @Override
            public OpenMeteoSnapshot fetchHistorical(GeoPoint point, Instant decisionTime) {
                return OpenMeteoSnapshot.unavailable();
            }
        };
        WeatherContextService service = new WeatherContextService(properties, staleClient);
        WeatherContextSnapshot snapshot = service.resolveWeather(request(WeatherProfile.HEAVY_RAIN), point());
        assertEquals(WeatherSource.DEGRADED_PROFILE, snapshot.source());
        assertTrue(snapshot.sourceAgeMs() > properties.getContext().getFreshness().getWeatherMaxAge().toMillis());
        assertTrue(snapshot.confidence() < 0.9);
    }

    private DispatchV2Request request(WeatherProfile weatherProfile) {
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-weather",
                List.of(),
                List.of(),
                List.of(),
                weatherProfile,
                Instant.parse("2026-04-16T12:00:00Z"));
    }

    private GeoPoint point() {
        return new GeoPoint(10.770, 106.690);
    }
}

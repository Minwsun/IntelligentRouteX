package com.routechain.v2.integration;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpOpenMeteoClientTest {

    @Test
    void happyPathMapsForecastIntoSnapshot() throws Exception {
        HttpServer server = HttpOpenMeteoTestSupport.server(Map.of(
                "/v1/forecast", HttpOpenMeteoTestSupport.json(HttpOpenMeteoTestSupport.weatherBody("2026-04-16T11:58:00Z", 65, 28.0))));
        try {
            HttpOpenMeteoClient client = new HttpOpenMeteoClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(100),
                    RouteChainDispatchV2Properties.defaults());

            OpenMeteoSnapshot snapshot = client.fetchForecast(new GeoPoint(10.77, 106.69), Instant.parse("2026-04-16T12:00:00Z"));

            assertTrue(snapshot.available());
            assertEquals("heavy-rain", snapshot.weatherCondition());
            assertTrue(snapshot.weatherBadSignal());
            assertEquals(1.28, snapshot.multiplier());
        } finally {
            server.stop(0);
        }
    }
}

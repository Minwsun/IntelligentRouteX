package com.routechain.v2.integration;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenMeteoClientFallbackContractTest {

    @Test
    void timeoutMalformedAndServerErrorReturnUnavailableSnapshot() throws Exception {
        assertUnavailable(Map.of("/v1/forecast", HttpOpenMeteoTestSupport.delayed(Duration.ofMillis(150), HttpOpenMeteoTestSupport.weatherBody("2026-04-16T11:58:00Z", 65, 28.0))));
        assertUnavailable(Map.of("/v1/forecast", HttpOpenMeteoTestSupport.json("{\"bad\":true}")));
        assertUnavailable(Map.of("/v1/forecast", HttpOpenMeteoTestSupport.status(500, "{\"error\":\"boom\"}")));
    }

    private void assertUnavailable(Map<String, com.sun.net.httpserver.HttpHandler> handlers) throws Exception {
        HttpServer server = HttpOpenMeteoTestSupport.server(handlers);
        try {
            HttpOpenMeteoClient client = new HttpOpenMeteoClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(60),
                    RouteChainDispatchV2Properties.defaults());

            OpenMeteoSnapshot snapshot = client.fetchForecast(new GeoPoint(10.77, 106.69), Instant.parse("2026-04-16T12:00:00Z"));

            assertFalse(snapshot.available());
            assertTrue(!snapshot.degradeReason().isBlank());
        } finally {
            server.stop(0);
        }
    }
}

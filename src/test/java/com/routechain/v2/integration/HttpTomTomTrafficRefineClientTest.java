package com.routechain.v2.integration;

import com.routechain.v2.context.EtaEstimateRequest;
import com.routechain.v2.context.TrafficRefineMapper;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTomTomTrafficRefineClientTest {

    @Test
    void happyPathMapsRefineResponse() throws Exception {
        HttpServer server = HttpTomTomTrafficTestSupport.server(Map.of(
                "/traffic/refine", HttpTomTomTrafficTestSupport.json(HttpTomTomTrafficTestSupport.refineBody(false, 1.18, 0L, 0.88, true))));
        try {
            HttpTomTomTrafficRefineClient client = new HttpTomTomTrafficRefineClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(100),
                    new TrafficRefineMapper());

            TomTomTrafficRefineResult result = client.refine(request(), 8.0, 2.0);

            assertTrue(result.applied());
            assertTrue(result.trafficBadSignal());
            assertTrue(result.multiplier() > 1.0);
        } finally {
            server.stop(0);
        }
    }

    private EtaEstimateRequest request() {
        return new EtaEstimateRequest("eta-estimate-request/v1", "trace-tomtom", new GeoPoint(10.77, 106.69), new GeoPoint(10.78, 106.70), Instant.parse("2026-04-16T12:00:00Z"), WeatherProfile.CLEAR, "eta/context", 150L);
    }
}

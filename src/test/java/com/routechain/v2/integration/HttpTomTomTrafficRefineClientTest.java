package com.routechain.v2.integration;

import com.routechain.v2.context.EtaEstimateRequest;
import com.routechain.v2.context.TrafficRefineMapper;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTomTomTrafficRefineClientTest {

    @Test
    void happyPathMapsFlowSegmentResponseAndSendsProviderQueryShape() throws Exception {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        AtomicReference<String> capturedTrackingId = new AtomicReference<>();
        HttpServer server = HttpTomTomTrafficTestSupport.server(Map.of(
                "/traffic/services/4/flowSegmentData/absolute/10/json",
                HttpTomTomTrafficTestSupport.capturingJson(
                        HttpTomTomTrafficTestSupport.flowSegmentBody(120.0, 90.0, 0.88, false),
                        capturedUri,
                        capturedTrackingId)));
        try {
            HttpTomTomTrafficRefineClient client = new HttpTomTomTrafficRefineClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-key",
                    Duration.ofMillis(100),
                    Duration.ofMillis(400),
                    new TrafficRefineMapper());

            TomTomTrafficRefineResult result = client.refine(request(), 8.0, 2.0);

            assertTrue(result.applied());
            assertTrue(result.trafficBadSignal());
            assertTrue(result.multiplier() > 1.0);
            assertTrue(capturedUri.get().getQuery().contains("point="));
            assertTrue(capturedUri.get().getQuery().contains("key=test-key"));
            assertTrue(capturedUri.get().getQuery().contains("unit=KMPH"));
            assertEquals("trace-tomtom", capturedTrackingId.get());
        } finally {
            server.stop(0);
        }
    }

    private EtaEstimateRequest request() {
        return new EtaEstimateRequest("eta-estimate-request/v1", "trace-tomtom", new GeoPoint(10.77, 106.69), new GeoPoint(10.78, 106.70), Instant.parse("2026-04-16T12:00:00Z"), WeatherProfile.CLEAR, "eta/context", 150L);
    }
}

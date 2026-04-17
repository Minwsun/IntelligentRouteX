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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomTomTrafficRefineFallbackContractTest {

    @Test
    void timeoutMalformedServerErrorAndFallbackReturnTypedNotAppliedResult() throws Exception {
        assertUnavailable(Map.of("/traffic/refine", HttpTomTomTrafficTestSupport.delayed(Duration.ofMillis(150), HttpTomTomTrafficTestSupport.refineBody(false, 1.18, 0L, 0.88, true))));
        assertUnavailable(Map.of("/traffic/refine", HttpTomTomTrafficTestSupport.json("{\"bad\":true}")));
        assertUnavailable(Map.of("/traffic/refine", HttpTomTomTrafficTestSupport.status(500, "{\"error\":\"boom\"}")));
        assertUnavailable(Map.of("/traffic/refine", HttpTomTomTrafficTestSupport.json(HttpTomTomTrafficTestSupport.refineBody(true, 1.18, 0L, 0.88, true))));
    }

    private void assertUnavailable(Map<String, com.sun.net.httpserver.HttpHandler> handlers) throws Exception {
        HttpServer server = HttpTomTomTrafficTestSupport.server(handlers);
        try {
            HttpTomTomTrafficRefineClient client = new HttpTomTomTrafficRefineClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(60),
                    new TrafficRefineMapper());

            TomTomTrafficRefineResult result = client.refine(request(), 8.0, 2.0);

            assertFalse(result.applied());
            assertTrue(!result.degradeReason().isBlank());
        } finally {
            server.stop(0);
        }
    }

    private EtaEstimateRequest request() {
        return new EtaEstimateRequest("eta-estimate-request/v1", "trace-tomtom", new GeoPoint(10.77, 106.69), new GeoPoint(10.78, 106.70), Instant.parse("2026-04-16T12:00:00Z"), WeatherProfile.CLEAR, "eta/context", 150L);
    }
}

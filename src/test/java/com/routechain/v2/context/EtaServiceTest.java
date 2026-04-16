package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.graph.OsmOsrmGraphProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaServiceTest {

    @Test
    void heavyRainEtaShouldBeWorseThanClearEta() {
        EtaService service = new EtaService(new OsmOsrmGraphProvider(), RouteChainDispatchV2Properties.defaults());
        GeoPoint start = new GeoPoint(10.7768, 106.7010);
        GeoPoint end = new GeoPoint(10.7820, 106.7070);

        EtaEstimate clear = service.estimate(start, end, Instant.parse("2026-04-16T01:00:00Z"), WeatherProfile.CLEAR, 0.20, false, "instant");
        EtaEstimate heavyRain = service.estimate(start, end, Instant.parse("2026-04-16T01:00:00Z"), WeatherProfile.HEAVY_RAIN, 0.20, false, "instant");

        assertTrue(heavyRain.etaMinutes() > clear.etaMinutes());
        assertTrue(heavyRain.etaUncertainty() >= clear.etaUncertainty());
    }
}

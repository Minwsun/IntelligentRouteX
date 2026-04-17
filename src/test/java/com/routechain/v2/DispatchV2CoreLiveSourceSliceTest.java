package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreLiveSourceSliceTest {

    @Test
    void keepsTwelveStagesAndCarriesLiveSourceMetadataWhenUsed() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);

        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestOpenMeteoClient.freshHeavyRain(),
                TestTomTomTrafficRefineClient.applied(1.15, true));

        DispatchV2Result result = harness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertTrue(result.liveStageMetadata().stream().anyMatch(metadata -> metadata.sourceName().equals("open-meteo")));
        assertTrue(harness.decisionLogService().latest().liveStageMetadata().stream().anyMatch(metadata -> metadata.sourceName().equals("tomtom-traffic")));
    }
}

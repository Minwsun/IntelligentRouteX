package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.TestForecastClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreForecastOfflineSliceTest {

    @Test
    void localForecastMetadataDoesNotChangeStageCountOrOutputContracts() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getForecast().setEnabled(true);

        DispatchV2Result result = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestForecastClient.applied(),
                new NoOpOpenMeteoClient(),
                new NoOpTomTomTrafficRefineClient())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertTrue(result.mlStageMetadata().stream().anyMatch(metadata -> metadata.stageName().equals("scenario-evaluation")
                && metadata.sourceModel().equals("chronos-2")
                && metadata.artifactDigest().equals("sha256:chronos")));
        assertTrue(result.assignments() != null);
    }
}

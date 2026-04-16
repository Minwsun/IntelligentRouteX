package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchRouteCandidateServiceTest {

    @Test
    void runsAnchorBeforeDriverShortlistAndConsumesBundleOutputs() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchRouteCandidateService service = RouteTestFixtures.routeService(properties);
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);

        DispatchRouteCandidateStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                pairClusterStage,
                bundleStage);

        assertFalse(stage.pickupAnchors().isEmpty());
        assertFalse(stage.driverCandidates().isEmpty());
        assertTrue(stage.pickupAnchors().stream().allMatch(anchor -> bundleStage.bundleCandidates().stream().anyMatch(bundle -> bundle.bundleId().equals(anchor.bundleId()))));
    }
}

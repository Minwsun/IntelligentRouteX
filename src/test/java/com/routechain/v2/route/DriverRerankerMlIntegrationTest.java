package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.TabularScoreResult;
import com.routechain.v2.integration.TestTabularScoringClient;
import com.routechain.v2.integration.WorkerReadyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverRerankerMlIntegrationTest {

    @Test
    void mlReranksWithinShortlistOnly() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        TestTabularScoringClient client = new TestTabularScoringClient(
                WorkerReadyState.ready(new MlWorkerMetadata("tabular-test", "v1", "sha256:test", 5L)),
                (feature, timeout) -> TabularScoreResult.notApplied("not-used"),
                (feature, timeout) -> TabularScoreResult.notApplied("not-used"),
                (feature, timeout) -> {
                    DriverFitFeatureVector vector = (DriverFitFeatureVector) feature;
                    return vector.driverId().equals("driver-2")
                            ? TabularScoreResult.applied(0.4, 0.1, false, new MlWorkerMetadata("tabular-test", "v1", "sha256:test", 5L))
                            : TabularScoreResult.applied(0.0, 0.1, false, new MlWorkerMetadata("tabular-test", "v1", "sha256:test", 5L));
                },
                (feature, timeout) -> TabularScoreResult.notApplied("not-used"));

        RouteChainDispatchV2Properties noMlProperties = RouteChainDispatchV2Properties.defaults();
        noMlProperties.getCandidate().setMaxDrivers(3);
        DispatchRouteCandidateStage deterministicStage = RouteTestFixtures.routeService(noMlProperties)
                .evaluate(RouteTestFixtures.request(), RouteTestFixtures.etaContext(), RouteTestFixtures.pairClusterStage(noMlProperties), RouteTestFixtures.bundleStage(noMlProperties, RouteTestFixtures.pairClusterStage(noMlProperties)));

        properties.getCandidate().setMaxDrivers(3);
        DispatchRouteCandidateStage mlStage = RouteTestFixtures.routeService(properties, client)
                .evaluate(RouteTestFixtures.request(), RouteTestFixtures.etaContext(), RouteTestFixtures.pairClusterStage(properties), RouteTestFixtures.bundleStage(properties, RouteTestFixtures.pairClusterStage(properties)));

        assertEquals(deterministicStage.driverCandidates().size(), mlStage.driverCandidates().size());
        assertEquals("driver-2", mlStage.driverCandidates().getFirst().driverId());
    }
}

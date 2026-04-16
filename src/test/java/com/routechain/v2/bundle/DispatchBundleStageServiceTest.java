package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchBundleStageServiceTest {

    @Test
    void runsBoundaryExpansionBeforeBundleGenerationAndUsesPairClusterOutputs() {
        DispatchBundleStageService service = new DispatchBundleStageService(
                RouteChainDispatchV2Properties.defaults(),
                new BoundaryCandidateSelector(RouteChainDispatchV2Properties.defaults()),
                new BoundaryExpansionEngine(RouteChainDispatchV2Properties.defaults()),
                new BundleSeedGenerator(RouteChainDispatchV2Properties.defaults()),
                new BundleFamilyEnumerator(RouteChainDispatchV2Properties.defaults()),
                new BundleValidator(RouteChainDispatchV2Properties.defaults()),
                new BundleScorer(RouteChainDispatchV2Properties.defaults()),
                new BundleDominancePruner());

        DispatchBundleStage stage = service.evaluate(
                BundleTestFixtures.clearEtaContext(),
                BundleTestFixtures.pairClusterStage());

        assertFalse(stage.boundaryExpansions().isEmpty());
        assertFalse(stage.bundleCandidates().isEmpty());
        assertTrue(stage.boundaryExpansions().stream().anyMatch(expansion -> !expansion.supportScoreByOrder().isEmpty()));
    }
}

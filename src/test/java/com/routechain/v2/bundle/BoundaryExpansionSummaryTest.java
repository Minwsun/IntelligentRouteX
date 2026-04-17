package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.NoOpGreedRlClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryExpansionSummaryTest {

    @Test
    void summaryMatchesExpansionOutputs() {
        DispatchBundleStageService service = bundleStageService(RouteChainDispatchV2Properties.defaults());

        DispatchBundleStage stage = service.evaluate(BundleTestFixtures.clearEtaContext(), BundleTestFixtures.pairClusterStage());

        int accepted = stage.boundaryExpansions().stream().mapToInt(expansion -> expansion.acceptedBoundaryOrderIds().size()).sum();
        int rejected = stage.boundaryExpansions().stream().mapToInt(expansion -> expansion.rejectedBoundaryOrderIds().size()).sum();
        assertEquals(accepted, stage.boundaryExpansionSummary().acceptedBoundaryOrderCount());
        assertEquals(rejected, stage.boundaryExpansionSummary().rejectedBoundaryOrderCount());
    }

    private DispatchBundleStageService bundleStageService(RouteChainDispatchV2Properties properties) {
        return new DispatchBundleStageService(
                properties,
                new BoundaryCandidateSelector(properties),
                new BoundaryExpansionEngine(properties),
                new BundleSeedGenerator(properties),
                new BundleFamilyEnumerator(properties),
                new BundleValidator(properties),
                new BundleScorer(properties),
                new BundleDominancePruner(),
                new NoOpGreedRlClient());
    }
}

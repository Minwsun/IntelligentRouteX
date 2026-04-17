package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.bundle.BundleProposalSource;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestGreedRlClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreGreedRlSliceTest {

    @Test
    void keepsTwelveStagesAndCarriesGreedRlMetadataWhenWorkerIsUsed() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                TestGreedRlClient.applied());

        DispatchV2Result result = harness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertTrue(result.bundlePoolSummary().sourceCounts().getOrDefault(BundleProposalSource.GREEDRL_PROPOSAL, 0) > 0);
        assertTrue(harness.decisionLogService().latest().mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("greedrl-local")));
    }
}

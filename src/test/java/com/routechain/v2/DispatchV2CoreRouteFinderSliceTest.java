package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestRouteFinderClient;
import com.routechain.v2.route.RouteProposalSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreRouteFinderSliceTest {

    @Test
    void keepsTwelveStagesAndCarriesRouteFinderMetadataWhenWorkerIsUsed() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getRoutefinder().setEnabled(true);
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(
                properties,
                new NoOpTabularScoringClient(),
                TestRouteFinderClient.applied());

        DispatchV2Result result = harness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertTrue(result.routeProposals().stream().anyMatch(proposal -> proposal.source() == RouteProposalSource.ML_PROPOSAL || proposal.source() == RouteProposalSource.ML_REFINED));
        assertTrue(harness.decisionLogService().latest().mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("routefinder-local")));
    }
}

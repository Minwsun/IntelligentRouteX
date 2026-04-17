package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CorePairClusterSliceTest {

    @Test
    void enabledPathReturnsPairClusterStagesAndOutputs() {
        DispatchV2Core core = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults());

        DispatchV2Result result = core.dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertFalse(result.fallbackUsed());
        assertNotNull(result.selectedRouteId());
        assertNotNull(result.etaContext());
        assertNotNull(result.bufferedOrderWindow());
        assertNotNull(result.pairGraphSummary());
        assertNotNull(result.microClusters());
        assertNotNull(result.microClusterSummary());
        assertNotNull(result.boundaryExpansionSummary());
        assertNotNull(result.bundlePoolSummary());
        assertNotNull(result.pickupAnchorSummary());
        assertNotNull(result.driverShortlistSummary());
        assertNotNull(result.routeProposalSummary());
        assertNotNull(result.scenarioEvaluationSummary());
        assertNotNull(result.globalSelectorSummary());
        assertNotNull(result.dispatchExecutionSummary());
        assertTrue(result.pairGraphSummary().candidatePairCount() > 0);
        assertFalse(result.microClusters().isEmpty());
    }
}

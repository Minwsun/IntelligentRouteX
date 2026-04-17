package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreTabularMlSliceTest {

    @Test
    void keepsTwelveStagesAndWritesMlMetadataWhenWorkerIsUsed() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(properties, TestTabularScoringClient.applied(0.05));

        DispatchV2Result result = harness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertFalse(result.mlStageMetadata().isEmpty());
        assertTrue(harness.decisionLogService().latest().mlStageMetadata().stream().anyMatch(metadata -> metadata.applied()));
    }

    @Test
    void dispatchStillSucceedsWhenTabularWorkerIsUnavailable() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);

        DispatchV2Result result = TestDispatchV2Factory.core(properties, TestTabularScoringClient.notApplied("tabular-unavailable"))
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertFalse(result.fallbackUsed());
        assertTrue(result.degradeReasons().contains("eta-ml-unavailable"));
    }
}

package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreScenarioSliceTest {

    @Test
    void enabledPathReturnsScenarioEvaluationOutputs() {
        DispatchV2Core core = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults());

        DispatchV2Result result = core.dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation"), result.decisionStages());
        assertFalse(result.fallbackUsed());
        assertNull(result.selectedRouteId());
        assertNotNull(result.scenarioEvaluationSummary());
        assertFalse(result.robustUtilities().isEmpty());
        assertTrue(result.scenarioEvaluations().stream().anyMatch(evaluation -> evaluation.scenario().name().equals("NORMAL")));
    }
}

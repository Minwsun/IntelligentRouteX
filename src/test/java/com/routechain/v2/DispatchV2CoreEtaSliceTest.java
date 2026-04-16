package com.routechain.v2;

import com.routechain.domain.WeatherProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreEtaSliceTest {

    @Test
    void returnsCurrentExecutedStagesForEnabledCore() {
        DispatchV2Core core = TestDispatchV2Factory.core(com.routechain.config.RouteChainDispatchV2Properties.defaults());
        DispatchV2Result result = core.dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());
        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster"), result.decisionStages());
        assertFalse(result.fallbackUsed());
        assertNull(result.selectedRouteId());
        assertNotNull(result.etaContext());
        assertNotNull(result.etaStageTrace());
        assertNotNull(result.freshnessMetadata());
        assertNotNull(result.bufferedOrderWindow());
        assertNotNull(result.pairGraphSummary());
        assertNotNull(result.microClusters());
        assertNotNull(result.microClusterSummary());
    }

    @Test
    void emptySamplingPolicyReturnsValidContextWithDegradeReason() {
        DispatchV2Core core = TestDispatchV2Factory.core(com.routechain.config.RouteChainDispatchV2Properties.defaults());
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-empty",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster"), result.decisionStages());
        assertEquals(0, result.etaContext().sampledLegCount());
        assertTrue(result.degradeReasons().contains("no-sampleable-eta-leg"));
        assertEquals(0, result.bufferedOrderWindow().orderCount());
        assertTrue(result.microClusters().isEmpty());
    }
}

package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DispatchV2CoreHardeningFlowTest {

    @Test
    void keepsTwelveDecisionStagesAndEmitsHardeningArtifactsPostPipeline() {
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(RouteChainDispatchV2Properties.defaults());

        DispatchV2Result result = harness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(12, result.decisionStages().size());
        assertNotNull(result.warmStartState());
        assertNotNull(result.hotStartState());
        assertNotNull(harness.dispatchReplayRecorder().latest());
        assertNotNull(harness.decisionLogService().latest());
        assertNotNull(harness.snapshotService().loadLatest().snapshot());
        assertFalse(result.fallbackUsed());
    }
}

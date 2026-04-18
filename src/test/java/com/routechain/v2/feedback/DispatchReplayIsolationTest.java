package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchReplayIsolationTest {

    @Test
    void replayDoesNotMutateLatestStoresOrAdvanceHotStartState() {
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(RouteChainDispatchV2Properties.defaults());
        DispatchV2Request originalRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result firstProductionResult = harness.core().dispatch(originalRequest);
        DecisionLogRecord latestDecisionLogBeforeReplay = harness.decisionLogService().latest();
        DispatchRuntimeSnapshot latestSnapshotBeforeReplay = harness.snapshotService().loadLatest().snapshot();
        DispatchRuntimeReuseState latestReuseStateBeforeReplay = harness.reuseStateService().loadLatest().reuseState();

        DispatchV2Request replayOnlyRequest = new DispatchV2Request(
                originalRequest.schemaVersion(),
                "trace-replay-only",
                originalRequest.openOrders(),
                originalRequest.availableDrivers(),
                originalRequest.regions(),
                originalRequest.weatherProfile(),
                originalRequest.decisionTime().plusSeconds(90));
        harness.dispatchReplayRecorder().record(replayOnlyRequest);

        ReplayRunResult replayRunResult = harness.dispatchReplayRunner().replayLatest();

        assertEquals(replayOnlyRequest.traceId(), replayRunResult.replayRequestRecord().traceId());
        assertEquals(latestDecisionLogBeforeReplay, harness.decisionLogService().latest());
        assertEquals(latestSnapshotBeforeReplay, harness.snapshotService().loadLatest().snapshot());
        assertEquals(latestReuseStateBeforeReplay, harness.reuseStateService().loadLatest().reuseState());

        DispatchV2Result secondProductionResult = harness.core().dispatch(originalRequest);
        assertEquals(firstProductionResult.traceId(), secondProductionResult.hotStartState().previousTraceId());
    }
}

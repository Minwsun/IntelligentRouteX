package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchReplayRunnerTest {

    @Test
    void rerunFromRecordedRequestMatchesStageListAndIds() {
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(RouteChainDispatchV2Properties.defaults());
        harness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        ReplayRunResult replayRunResult = harness.dispatchReplayRunner().replayLatest();

        assertTrue(replayRunResult.comparisonResult().matched());
        assertEquals(12, replayRunResult.replayDecisionStages().size());
        assertEquals(replayRunResult.replaySelectedProposalIds().size(), replayRunResult.replaySelectedCount());
        assertEquals(replayRunResult.replayExecutedAssignmentIds().size(), replayRunResult.replayExecutedAssignmentCount());
        assertNotNull(replayRunResult.referenceDecisionLog());
        assertNotNull(replayRunResult.referenceSnapshotManifest());
    }
}

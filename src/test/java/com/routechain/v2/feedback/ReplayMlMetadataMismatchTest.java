package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.TabularScoreResult;
import com.routechain.v2.integration.TestTabularScoringClient;
import com.routechain.v2.integration.WorkerReadyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayMlMetadataMismatchTest {

    @Test
    void replayReportsExplicitModelMetadataMismatchReasons() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        DispatchV2Result referenceResult = TestDispatchV2Factory.core(properties, TestTabularScoringClient.applied(0.05))
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());
        DecisionLogRecord reference = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), referenceResult);

        TestTabularScoringClient driftedClient = new TestTabularScoringClient(
                WorkerReadyState.ready(new MlWorkerMetadata("tabular-test", "v2", "sha256:other", 5L)),
                (feature, timeout) -> TabularScoreResult.applied(0.05, 0.1, false, new MlWorkerMetadata("tabular-test", "v2", "sha256:other", 5L)),
                (feature, timeout) -> TabularScoreResult.applied(0.05, 0.1, false, new MlWorkerMetadata("tabular-test", "v2", "sha256:other", 5L)),
                (feature, timeout) -> TabularScoreResult.applied(0.05, 0.1, false, new MlWorkerMetadata("tabular-test", "v2", "sha256:other", 5L)),
                (feature, timeout) -> TabularScoreResult.applied(0.05, 0.1, false, new MlWorkerMetadata("tabular-test", "v2", "sha256:other", 5L)));
        DispatchV2Result replayResult = TestDispatchV2Factory.core(properties, driftedClient)
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        ReplayComparisonResult comparisonResult = new DispatchReplayComparator().compare(reference, null, replayResult);

        assertTrue(comparisonResult.mismatchReasons().contains("ml-model-version-mismatch"));
        assertTrue(comparisonResult.mismatchReasons().contains("ml-artifact-digest-mismatch"));
    }
}

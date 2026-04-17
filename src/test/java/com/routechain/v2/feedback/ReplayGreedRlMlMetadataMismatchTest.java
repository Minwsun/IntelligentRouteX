package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.GreedRlBundleFeatureVector;
import com.routechain.v2.integration.GreedRlBundleResult;
import com.routechain.v2.integration.GreedRlSequenceFeatureVector;
import com.routechain.v2.integration.GreedRlSequenceResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestGreedRlClient;
import com.routechain.v2.integration.WorkerReadyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayGreedRlMlMetadataMismatchTest {

    @Test
    void replayReportsExplicitGreedRlModelMetadataMismatchReasons() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);
        DispatchV2Result referenceResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                TestGreedRlClient.applied())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());
        DecisionLogRecord reference = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), referenceResult);

        MlWorkerMetadata driftedMetadata = new MlWorkerMetadata("greedrl-local", "v2", "sha256:other", 9L);
        TestGreedRlClient driftedClient = new TestGreedRlClient(
                WorkerReadyState.ready(driftedMetadata),
                (GreedRlBundleFeatureVector feature, Long timeout) -> GreedRlBundleResult.applied(TestGreedRlClient.applied().proposeBundles(feature, timeout).proposals(), driftedMetadata),
                (GreedRlSequenceFeatureVector feature, Long timeout) -> GreedRlSequenceResult.applied(TestGreedRlClient.applied().proposeSequence(feature, timeout).sequences(), driftedMetadata));
        DispatchV2Result replayResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                driftedClient)
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        ReplayComparisonResult comparisonResult = new DispatchReplayComparator().compare(reference, null, replayResult);

        assertTrue(comparisonResult.mismatchReasons().contains("ml-model-version-mismatch"));
        assertTrue(comparisonResult.mismatchReasons().contains("ml-artifact-digest-mismatch"));
    }
}

package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.RouteFinderFeatureVector;
import com.routechain.v2.integration.RouteFinderResult;
import com.routechain.v2.integration.TestRouteFinderClient;
import com.routechain.v2.integration.WorkerReadyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayRouteFinderMlMetadataMismatchTest {

    @Test
    void replayReportsExplicitRouteFinderModelMetadataMismatchReasons() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getRoutefinder().setEnabled(true);
        DispatchV2Result referenceResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                TestRouteFinderClient.applied())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());
        DecisionLogRecord reference = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), referenceResult);

        MlWorkerMetadata driftedMetadata = new MlWorkerMetadata("routefinder-local", "v2", "sha256:other", 7L);
        TestRouteFinderClient driftedClient = new TestRouteFinderClient(
                WorkerReadyState.ready(driftedMetadata),
                (RouteFinderFeatureVector feature, Long timeout) -> RouteFinderResult.applied(TestRouteFinderClient.applied().refineRoute(feature, timeout).routes(), false, driftedMetadata),
                (RouteFinderFeatureVector feature, Long timeout) -> RouteFinderResult.applied(TestRouteFinderClient.applied().generateAlternatives(feature, timeout).routes(), false, driftedMetadata));
        DispatchV2Result replayResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                driftedClient)
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        ReplayComparisonResult comparisonResult = new DispatchReplayComparator().compare(reference, null, replayResult);

        assertTrue(comparisonResult.mismatchReasons().contains("ml-model-version-mismatch"));
        assertTrue(comparisonResult.mismatchReasons().contains("ml-artifact-digest-mismatch"));
    }
}

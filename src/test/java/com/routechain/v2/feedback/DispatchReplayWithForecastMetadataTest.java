package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.ForecastResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.TestForecastClient;
import com.routechain.v2.integration.DemandShiftFeatureVector;
import com.routechain.v2.integration.PostDropShiftFeatureVector;
import com.routechain.v2.integration.ZoneBurstFeatureVector;
import com.routechain.v2.integration.WorkerReadyState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchReplayWithForecastMetadataTest {

    @Test
    void replayReportsExplicitChronosMetadataMismatchReasons() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getForecast().setEnabled(true);
        DispatchV2Result referenceResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestForecastClient.applied(),
                new NoOpOpenMeteoClient(),
                new NoOpTomTomTrafficRefineClient())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());
        DecisionLogRecord reference = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), referenceResult);

        MlWorkerMetadata driftedMetadata = new MlWorkerMetadata("chronos-2", "v2", "sha256:other", 11L);
        TestForecastClient driftedClient = new TestForecastClient(
                WorkerReadyState.ready(driftedMetadata),
                (DemandShiftFeatureVector feature, Long timeout) -> ForecastResult.applied(30, 0.71, Map.of("q50", -0.09), 0.84, 90000L, driftedMetadata),
                (ZoneBurstFeatureVector feature, Long timeout) -> ForecastResult.applied(20, 0.74, Map.of("q50", 0.16), 0.82, 80000L, driftedMetadata),
                (PostDropShiftFeatureVector feature, Long timeout) -> ForecastResult.applied(45, 0.69, Map.of("q50", 0.12), 0.8, 85000L, driftedMetadata));
        DispatchV2Result replayResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                driftedClient,
                new NoOpOpenMeteoClient(),
                new NoOpTomTomTrafficRefineClient())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        ReplayComparisonResult comparisonResult = new DispatchReplayComparator().compare(reference, null, replayResult);

        assertTrue(comparisonResult.mismatchReasons().contains("ml-model-version-mismatch"));
        assertTrue(comparisonResult.mismatchReasons().contains("ml-artifact-digest-mismatch"));
    }
}

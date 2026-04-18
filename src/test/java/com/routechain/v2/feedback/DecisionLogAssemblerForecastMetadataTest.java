package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.TestForecastClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionLogAssemblerForecastMetadataTest {

    @Test
    void logsForecastMetadataWithoutRecomputation() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getForecast().setEnabled(true);

        DispatchV2Result result = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestForecastClient.applied(),
                new NoOpOpenMeteoClient(),
                new NoOpTomTomTrafficRefineClient())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        DecisionLogRecord record = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), result);

        assertTrue(record.mlStageMetadata().stream().anyMatch(metadata -> metadata.stageName().equals("scenario-evaluation")
                && metadata.sourceModel().equals("chronos-2")));
        assertEquals(result.stageLatencies(), record.stageLatencies());
        assertEquals(result.latencyBudgetSummary(), record.latencyBudgetSummary());
    }
}

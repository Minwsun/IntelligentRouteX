package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestForecastClient;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchScenarioServiceForecastIntegrationTest {

    @Test
    void forecastCallsAreBoundedToOncePerDispatchContextAndMetadataIsCarried() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getForecast().setEnabled(true);
        TestForecastClient forecastClient = TestForecastClient.applied();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        DispatchScenarioService service = RouteTestFixtures.scenarioService(properties, forecastClient);

        DispatchScenarioStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                new com.routechain.v2.context.FreshnessMetadata("freshness-metadata/v1", 0L, 0L, 0L, true, true, false),
                com.routechain.v2.LiveStageMetadata.emptyList(),
                routeProposalStage,
                routeCandidateStage,
                bundleStage,
                pairClusterStage);

        assertEquals(3, forecastClient.invocations().size());
        assertTrue(stage.mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("chronos-2")));
        assertTrue(stage.freshnessMetadata().forecastFresh());
        assertTrue(stage.scenarioEvaluationSummary().appliedScenarioCounts().getOrDefault(ScenarioType.ZONE_BURST, 0) > 0);
    }
}

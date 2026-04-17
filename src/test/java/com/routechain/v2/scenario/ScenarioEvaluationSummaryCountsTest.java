package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.context.FreshnessMetadata;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioEvaluationSummaryCountsTest {

    @Test
    void emittedAndAppliedCountsAreTrackedSeparately() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        DispatchScenarioService service = RouteTestFixtures.scenarioService(properties);

        DispatchScenarioStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                new FreshnessMetadata("freshness-metadata/v1", 0L, 0L, 0L, true, true, false),
                LiveStageMetadata.emptyList(),
                routeProposalStage,
                routeCandidateStage,
                bundleStage,
                pairClusterStage);

        assertEquals(routeProposalStage.routeProposals().size() * ScenarioType.values().length, stage.scenarioEvaluationSummary().scenarioEvaluationCount());
        assertEquals(routeProposalStage.routeProposals().size(), stage.scenarioEvaluationSummary().scenarioCounts().get(ScenarioType.NORMAL));
        assertTrue(stage.scenarioEvaluationSummary().appliedScenarioCount() < stage.scenarioEvaluationSummary().scenarioEvaluationCount());
        assertEquals(0, stage.scenarioEvaluationSummary().appliedScenarioCounts().getOrDefault(ScenarioType.DEMAND_SHIFT, 0));
    }
}

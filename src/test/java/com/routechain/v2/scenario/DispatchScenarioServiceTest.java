package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchScenarioServiceTest {

    @Test
    void evaluatesProposalsAfterProposalStageAndProducesRobustUtilities() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        DispatchScenarioService service = RouteTestFixtures.scenarioService(properties);

        DispatchScenarioStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                routeProposalStage,
                routeCandidateStage,
                bundleStage,
                pairClusterStage);

        assertFalse(stage.scenarioEvaluations().isEmpty());
        assertFalse(stage.robustUtilities().isEmpty());
        assertTrue(stage.robustUtilities().stream().allMatch(utility -> routeProposalStage.routeProposals().stream().anyMatch(proposal -> proposal.proposalId().equals(utility.proposalId()))));
    }
}

package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteProposalMlIsolationTest {

    @Test
    void routeValueMlChangesOrderingOnlyAndDoesNotDriftProposalCountOrValidity() {
        RouteChainDispatchV2Properties deterministicProperties = RouteChainDispatchV2Properties.defaults();
        RouteChainDispatchV2Properties mlProperties = RouteChainDispatchV2Properties.defaults();
        mlProperties.setMlEnabled(true);
        mlProperties.getMl().getTabular().setEnabled(true);

        DispatchRouteProposalStage deterministic = RouteTestFixtures.routeProposalService(deterministicProperties)
                .evaluate(RouteTestFixtures.request(), RouteTestFixtures.etaContext(), RouteTestFixtures.pairClusterStage(deterministicProperties), RouteTestFixtures.bundleStage(deterministicProperties, RouteTestFixtures.pairClusterStage(deterministicProperties)), RouteTestFixtures.routeCandidateStage(deterministicProperties));
        DispatchRouteProposalStage adjusted = RouteTestFixtures.routeProposalService(mlProperties, TestTabularScoringClient.applied(0.05))
                .evaluate(RouteTestFixtures.request(), RouteTestFixtures.etaContext(), RouteTestFixtures.pairClusterStage(mlProperties), RouteTestFixtures.bundleStage(mlProperties, RouteTestFixtures.pairClusterStage(mlProperties)), RouteTestFixtures.routeCandidateStage(mlProperties));

        assertEquals(deterministic.routeProposalSummary().proposalCount(), adjusted.routeProposalSummary().proposalCount());
        assertEquals(deterministic.routeProposalSummary().retainedProposalCount(), adjusted.routeProposalSummary().retainedProposalCount());
        assertEquals(deterministic.routeProposals().stream().filter(RouteProposal::feasible).count(), adjusted.routeProposals().stream().filter(RouteProposal::feasible).count());
    }
}

package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteProposalIdDeterminismTest {

    @Test
    void sameInputYieldsStableProposalIdsSourcesAndTupleKeys() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalService service = RouteTestFixtures.routeProposalService(properties);

        DispatchRouteProposalStage first = service.evaluate(RouteTestFixtures.request(), RouteTestFixtures.etaContext(), pairClusterStage, bundleStage, routeCandidateStage);
        DispatchRouteProposalStage second = service.evaluate(RouteTestFixtures.request(), RouteTestFixtures.etaContext(), pairClusterStage, bundleStage, routeCandidateStage);

        assertEquals(first.routeProposals().stream().map(RouteProposal::proposalId).toList(), second.routeProposals().stream().map(RouteProposal::proposalId).toList());
        assertEquals(first.routeProposals().stream().map(RouteProposal::source).toList(), second.routeProposals().stream().map(RouteProposal::source).toList());
        assertEquals(first.routeProposals().stream().map(RouteProposal::stopOrder).toList(), second.routeProposals().stream().map(RouteProposal::stopOrder).toList());
    }
}

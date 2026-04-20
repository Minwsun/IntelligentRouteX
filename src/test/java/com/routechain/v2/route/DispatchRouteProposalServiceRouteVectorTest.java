package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchRouteProposalServiceRouteVectorTest {

    @Test
    void evaluateAddsRouteVectorFieldsToGeneratedProposals() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchRouteProposalStage stage = RouteTestFixtures.routeProposalStage(properties);

        assertFalse(stage.routeProposals().isEmpty());
        assertTrue(stage.routeProposals().stream().allMatch(RouteProposal::geometryAvailable));
        assertTrue(stage.routeProposals().stream().allMatch(proposal -> proposal.totalDistanceMeters() > 0.0));
    }
}

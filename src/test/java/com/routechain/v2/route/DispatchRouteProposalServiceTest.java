package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchRouteProposalServiceTest {

    @Test
    void buildsRouteProposalsAfterDriverShortlistAndUsesCandidateContext() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalService service = RouteTestFixtures.routeProposalService(properties);

        DispatchRouteProposalStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage);

        assertFalse(stage.routeProposals().isEmpty());
        assertTrue(stage.routeProposals().stream().allMatch(proposal -> routeCandidateStage.driverCandidates().stream().anyMatch(driverCandidate ->
                driverCandidate.bundleId().equals(proposal.bundleId())
                        && driverCandidate.anchorOrderId().equals(proposal.anchorOrderId())
                        && driverCandidate.driverId().equals(proposal.driverId()))));
        assertTrue(stage.routeProposalSummary().proposalTupleCount() > 0);
    }
}

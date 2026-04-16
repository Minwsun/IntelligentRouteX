package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteProposalValidatorTest {

    @Test
    void marksInvalidProposalWhenAnchorDoesNotLeadStopOrder() {
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(RouteChainDispatchV2Properties.defaults());
        String bundleId = context.bundleIds().getFirst();
        PickupAnchor pickupAnchor = new PickupAnchor("pickup-anchor/v1", bundleId, context.orderSetSignature(bundleId), context.bundle(bundleId).seedOrderId(), 1, 0.8, List.of());
        DriverCandidate driverCandidate = new DriverCandidate("driver-candidate/v1", bundleId, pickupAnchor.anchorOrderId(), context.availableDrivers().getFirst().driverId(), 1, 5.0, 0.8, 0.8, List.of(), List.of());
        RouteProposal proposal = new RouteProposal(
                "route-proposal/v1",
                "proposal-1",
                bundleId,
                pickupAnchor.anchorOrderId(),
                driverCandidate.driverId(),
                RouteProposalSource.HEURISTIC_FAST,
                List.of("order-2", pickupAnchor.anchorOrderId()),
                4.0,
                12.0,
                0.0,
                false,
                List.of(),
                List.of());

        RouteProposalCandidate validated = new RouteProposalValidator().validate(
                new RouteProposalCandidate(
                        proposal,
                        new RouteProposalTupleKey(bundleId, pickupAnchor.anchorOrderId(), driverCandidate.driverId()),
                        pickupAnchor,
                        driverCandidate,
                        new RouteProposalTrace(new RouteProposalTupleKey(bundleId, pickupAnchor.anchorOrderId(), driverCandidate.driverId()), RouteProposalSource.HEURISTIC_FAST, "order-2>" + pickupAnchor.anchorOrderId(), 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of())),
                context);

        assertFalse(validated.proposal().feasible());
        assertTrue(validated.proposal().reasons().contains("route-proposal-anchor-must-lead-stop-order"));
    }
}

package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteValueScorerMlIntegrationTest {

    @Test
    void workerUpAdjustsRouteValueAndWorkerDownPreservesDeterministicValue() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        String bundleId = context.bundleIds().getFirst();
        PickupAnchor pickupAnchor = new PickupAnchor("pickup-anchor/v1", bundleId, context.orderSetSignature(bundleId), context.bundle(bundleId).seedOrderId(), 1, 0.8, List.of());
        DriverCandidate driverCandidate = new DriverCandidate("driver-candidate/v1", bundleId, pickupAnchor.anchorOrderId(), context.availableDrivers().getFirst().driverId(), 1, 5.0, 0.8, 0.8, List.of(), List.of());
        RouteProposalCandidate candidate = candidate(bundleId, pickupAnchor, driverCandidate, RouteProposalSource.HEURISTIC_FAST, 4.0, 20.0);

        RouteProposalCandidate deterministic = new RouteValueScorer(properties, TestTabularScoringClient.notApplied("tabular-unavailable"))
                .score(RouteTestFixtures.request().traceId(), candidate, context)
                .candidate();
        RouteProposalCandidate adjusted = new RouteValueScorer(properties, TestTabularScoringClient.applied(0.1))
                .score(RouteTestFixtures.request().traceId(), candidate, context)
                .candidate();

        assertTrue(adjusted.proposal().routeValue() > deterministic.proposal().routeValue());
    }

    private RouteProposalCandidate candidate(String bundleId,
                                             PickupAnchor pickupAnchor,
                                             DriverCandidate driverCandidate,
                                             RouteProposalSource source,
                                             double projectedPickupEta,
                                             double projectedCompletionEta) {
        RouteProposal proposal = new RouteProposal(
                "route-proposal/v1",
                bundleId + "|" + source.name(),
                bundleId,
                pickupAnchor.anchorOrderId(),
                driverCandidate.driverId(),
                source,
                List.copyOf(RouteTestFixtures.candidateContext(RouteChainDispatchV2Properties.defaults()).bundle(bundleId).orderIds()),
                projectedPickupEta,
                projectedCompletionEta,
                0.0,
                true,
                List.of(),
                List.of());
        RouteProposalTupleKey tupleKey = new RouteProposalTupleKey(bundleId, pickupAnchor.anchorOrderId(), driverCandidate.driverId());
        return new RouteProposalCandidate(
                proposal,
                tupleKey,
                pickupAnchor,
                driverCandidate,
                new RouteProposalTrace(tupleKey, source, RouteProposalEngine.stopOrderSignature(proposal.stopOrder()), 0, 0, 0, 0, 0, 0, 0, 0, source == RouteProposalSource.FALLBACK_SIMPLE ? 0.05 : 0.0, List.of()));
    }
}

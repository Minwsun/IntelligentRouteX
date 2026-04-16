package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteValueScorerTest {

    @Test
    void prefersBetterPickupEtaAndPenalizesFallbackSource() {
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(RouteChainDispatchV2Properties.defaults());
        String bundleId = context.bundleIds().getFirst();
        PickupAnchor pickupAnchor = new PickupAnchor("pickup-anchor/v1", bundleId, context.orderSetSignature(bundleId), context.bundle(bundleId).seedOrderId(), 1, 0.8, List.of());
        DriverCandidate driverCandidate = new DriverCandidate("driver-candidate/v1", bundleId, pickupAnchor.anchorOrderId(), context.availableDrivers().getFirst().driverId(), 1, 5.0, 0.8, 0.8, List.of(), List.of());
        RouteValueScorer scorer = new RouteValueScorer();

        RouteProposalCandidate faster = scorer.score(candidate(bundleId, pickupAnchor, driverCandidate, RouteProposalSource.HEURISTIC_FAST, 4.0, 20.0), context);
        RouteProposalCandidate fallback = scorer.score(candidate(bundleId, pickupAnchor, driverCandidate, RouteProposalSource.FALLBACK_SIMPLE, 4.0, 20.0), context);
        RouteProposalCandidate slower = scorer.score(candidate(bundleId, pickupAnchor, driverCandidate, RouteProposalSource.HEURISTIC_FAST, 8.0, 20.0), context);

        assertTrue(faster.proposal().routeValue() > slower.proposal().routeValue());
        assertTrue(faster.proposal().routeValue() > fallback.proposal().routeValue());
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

package com.routechain.v2.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RouteProposalValidator {

    public RouteProposalCandidate validate(RouteProposalCandidate candidate, DispatchCandidateContext context) {
        List<String> reasons = new ArrayList<>(candidate.proposal().reasons());
        List<String> validationReasons = new ArrayList<>();
        RouteProposal proposal = candidate.proposal();
        if (proposal.stopOrder().isEmpty()) {
            validationReasons.add("route-proposal-empty-stop-order");
        }
        if (!proposal.stopOrder().isEmpty() && !proposal.stopOrder().getFirst().equals(proposal.anchorOrderId())) {
            validationReasons.add("route-proposal-anchor-must-lead-stop-order");
        }
        if (context.bundle(proposal.bundleId()) == null) {
            validationReasons.add("route-proposal-missing-bundle-context");
        }
        Set<String> bundleOrderIds = context.bundle(proposal.bundleId()) == null
                ? Set.of()
                : new HashSet<>(context.bundle(proposal.bundleId()).orderIds());
        Set<String> stopOrderIds = new HashSet<>(proposal.stopOrder());
        if (stopOrderIds.size() != proposal.stopOrder().size()) {
            validationReasons.add("route-proposal-contains-duplicate-orders");
        }
        if (!bundleOrderIds.equals(stopOrderIds)) {
            validationReasons.add("route-proposal-order-set-mismatch");
        }
        if (proposal.projectedPickupEtaMinutes() < 0.0 || proposal.projectedCompletionEtaMinutes() < proposal.projectedPickupEtaMinutes()) {
            validationReasons.add("route-proposal-invalid-projected-eta");
        }
        reasons.addAll(validationReasons);
        RouteProposal validated = new RouteProposal(
                proposal.schemaVersion(),
                proposal.proposalId(),
                proposal.bundleId(),
                proposal.anchorOrderId(),
                proposal.driverId(),
                proposal.source(),
                proposal.stopOrder(),
                proposal.projectedPickupEtaMinutes(),
                proposal.projectedCompletionEtaMinutes(),
                proposal.routeValue(),
                validationReasons.isEmpty(),
                List.copyOf(reasons),
                proposal.degradeReasons(),
                proposal.legCount(),
                proposal.totalDistanceMeters(),
                proposal.totalTravelTimeSeconds(),
                proposal.routeCost(),
                proposal.majorRoadRatio(),
                proposal.minorRoadRatio(),
                proposal.turnCount(),
                proposal.uTurnCount(),
                proposal.congestionScore(),
                proposal.straightnessScore(),
                proposal.geometryAvailable(),
                proposal.legs());
        return new RouteProposalCandidate(
                validated,
                candidate.tupleKey(),
                candidate.pickupAnchor(),
                candidate.driverCandidate(),
                new RouteProposalTrace(
                        candidate.trace().tupleKey(),
                        candidate.trace().source(),
                        candidate.trace().stopOrderSignature(),
                        candidate.trace().driverRerankContribution(),
                        candidate.trace().bundleContribution(),
                        candidate.trace().anchorContribution(),
                        candidate.trace().pickupEtaContribution(),
                        candidate.trace().completionEtaContribution(),
                        candidate.trace().supportContribution(),
                        candidate.trace().urgencyLift(),
                        candidate.trace().boundaryPenalty(),
                        candidate.trace().fallbackPenalty(),
                        List.copyOf(validationReasons)));
    }
}

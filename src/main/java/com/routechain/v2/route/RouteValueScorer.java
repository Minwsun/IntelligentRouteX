package com.routechain.v2.route;

import com.routechain.v2.bundle.BundleCandidate;

import java.util.ArrayList;
import java.util.List;

public final class RouteValueScorer {

    public RouteProposalCandidate score(RouteProposalCandidate candidate, DispatchCandidateContext context) {
        RouteProposal proposal = candidate.proposal();
        if (!proposal.feasible()) {
            return new RouteProposalCandidate(
                    new RouteProposal(
                            proposal.schemaVersion(),
                            proposal.proposalId(),
                            proposal.bundleId(),
                            proposal.anchorOrderId(),
                            proposal.driverId(),
                            proposal.source(),
                            proposal.stopOrder(),
                            proposal.projectedPickupEtaMinutes(),
                            proposal.projectedCompletionEtaMinutes(),
                            0.0,
                            false,
                            proposal.reasons(),
                            proposal.degradeReasons()),
                    candidate.tupleKey(),
                    candidate.pickupAnchor(),
                    candidate.driverCandidate(),
                    candidate.trace());
        }
        BundleCandidate bundle = context.bundle(proposal.bundleId());
        double driverContribution = 0.32 * candidate.driverCandidate().rerankScore();
        double bundleContribution = 0.20 * bundle.score();
        double anchorContribution = 0.14 * candidate.pickupAnchor().score();
        double pickupEtaContribution = 0.14 * etaScore(proposal.projectedPickupEtaMinutes(), 25.0);
        double completionEtaContribution = 0.10 * etaScore(proposal.projectedCompletionEtaMinutes(), 75.0);
        double supportContribution = 0.10 * context.averagePairSupport(bundle.orderIds());
        double urgencyLift = bundle.orderIds().stream()
                .map(context::order)
                .filter(java.util.Objects::nonNull)
                .anyMatch(order -> order.urgent() && proposal.projectedPickupEtaMinutes() <= 12.0) ? 0.05 : 0.0;
        double boundaryPenalty = bundle.boundaryCross() ? Math.max(0.0, 0.08 - context.acceptedBoundarySupport(bundle.bundleId()) * 0.08) : 0.0;
        double fallbackPenalty = proposal.source() == RouteProposalSource.FALLBACK_SIMPLE ? 0.05 : 0.0;
        double score = Math.max(0.0, Math.min(1.0,
                driverContribution
                        + bundleContribution
                        + anchorContribution
                        + pickupEtaContribution
                        + completionEtaContribution
                        + supportContribution
                        + urgencyLift
                        - boundaryPenalty
                        - fallbackPenalty));
        List<String> reasons = new ArrayList<>(proposal.reasons());
        if (urgencyLift > 0.0) {
            reasons.add("urgent-route-lift");
        }
        if (fallbackPenalty > 0.0) {
            reasons.add("fallback-simple-penalty");
        }
        RouteProposal scored = new RouteProposal(
                proposal.schemaVersion(),
                proposal.proposalId(),
                proposal.bundleId(),
                proposal.anchorOrderId(),
                proposal.driverId(),
                proposal.source(),
                proposal.stopOrder(),
                proposal.projectedPickupEtaMinutes(),
                proposal.projectedCompletionEtaMinutes(),
                score,
                true,
                List.copyOf(reasons),
                proposal.degradeReasons());
        return new RouteProposalCandidate(
                scored,
                candidate.tupleKey(),
                candidate.pickupAnchor(),
                candidate.driverCandidate(),
                new RouteProposalTrace(
                        candidate.trace().tupleKey(),
                        candidate.trace().source(),
                        candidate.trace().stopOrderSignature(),
                        driverContribution,
                        bundleContribution,
                        anchorContribution,
                        pickupEtaContribution,
                        completionEtaContribution,
                        supportContribution,
                        urgencyLift,
                        boundaryPenalty,
                        fallbackPenalty,
                        candidate.trace().validationReasons()));
    }

    private double etaScore(double etaMinutes, double ceilingMinutes) {
        return Math.max(0.0, 1.0 - (etaMinutes / ceilingMinutes));
    }
}

package com.routechain.v2.scenario;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.integration.DemandShiftFeatureVector;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteProposalStage;

public final class DemandShiftFeatureBuilder {

    public DemandShiftFeatureVector build(DispatchV2Request request,
                                          EtaContext etaContext,
                                          DispatchCandidateContext context,
                                          DispatchRouteProposalStage routeProposalStage,
                                          DispatchBundleStage bundleStage) {
        double averageReadySpread = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> context.readyTimeSpread(proposal.bundleId()))
                .average()
                .orElse(0.0);
        double averagePickupEta = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.projectedPickupEtaMinutes())
                .average()
                .orElse(etaContext.averageEtaMinutes());
        double averageCompletionEta = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.projectedCompletionEtaMinutes())
                .average()
                .orElse(etaContext.maxEtaMinutes());
        double averageRouteValue = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.routeValue())
                .average()
                .orElse(0.0);
        double averageBoundaryParticipation = bundleStage.bundleCandidates().stream()
                .mapToDouble(bundle -> context.acceptedBoundaryParticipation(bundle.bundleId()))
                .average()
                .orElse(0.0);
        int urgentOrderCount = (int) request.openOrders().stream().filter(order -> order.urgent()).count();
        return new DemandShiftFeatureVector(
                "demand-shift-feature-vector/v1",
                request.traceId(),
                etaContext.corridorId(),
                request.openOrders().size(),
                urgentOrderCount,
                request.availableDrivers().size(),
                averageReadySpread,
                averagePickupEta,
                averageCompletionEta,
                averageRouteValue,
                averageBoundaryParticipation,
                30);
    }
}

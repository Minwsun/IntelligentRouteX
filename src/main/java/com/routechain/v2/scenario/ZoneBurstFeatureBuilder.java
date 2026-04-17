package com.routechain.v2.scenario;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.integration.ZoneBurstFeatureVector;

public final class ZoneBurstFeatureBuilder {

    public ZoneBurstFeatureVector build(DispatchV2Request request,
                                        EtaContext etaContext,
                                        DispatchCandidateContext context,
                                        DispatchRouteProposalStage routeProposalStage) {
        double averageCompletionEta = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.projectedCompletionEtaMinutes())
                .average()
                .orElse(etaContext.maxEtaMinutes());
        double averageRouteValue = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.routeValue())
                .average()
                .orElse(0.0);
        double averageBundleScore = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> context.bundleScore(proposal.bundleId()))
                .average()
                .orElse(0.0);
        double averagePairSupport = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> context.averagePairSupport(context.bundle(proposal.bundleId()).orderIds()))
                .average()
                .orElse(0.0);
        int urgentOrderCount = (int) request.openOrders().stream().filter(order -> order.urgent()).count();
        return new ZoneBurstFeatureVector(
                "zone-burst-feature-vector/v1",
                request.traceId(),
                etaContext.corridorId(),
                request.openOrders().size(),
                urgentOrderCount,
                request.availableDrivers().size(),
                averageCompletionEta,
                averageRouteValue,
                averageBundleScore,
                averagePairSupport,
                20);
    }
}

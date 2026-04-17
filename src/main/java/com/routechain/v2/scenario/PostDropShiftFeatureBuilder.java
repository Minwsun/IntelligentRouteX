package com.routechain.v2.scenario;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.integration.PostDropShiftFeatureVector;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;

import java.util.Map;
import java.util.stream.Collectors;

public final class PostDropShiftFeatureBuilder {

    public PostDropShiftFeatureVector build(DispatchV2Request request,
                                            EtaContext etaContext,
                                            DispatchCandidateContext context,
                                            DispatchRouteProposalStage routeProposalStage,
                                            DispatchRouteCandidateStage routeCandidateStage) {
        Map<String, DriverCandidate> driverCandidateByKey = routeCandidateStage.driverCandidates().stream()
                .collect(Collectors.toMap(
                        candidate -> candidate.bundleId() + "|" + candidate.anchorOrderId() + "|" + candidate.driverId(),
                        candidate -> candidate,
                        (left, right) -> left));
        double averageDriverRerankScore = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> driverCandidateByKey.getOrDefault(
                                proposal.bundleId() + "|" + proposal.anchorOrderId() + "|" + proposal.driverId(),
                                new DriverCandidate("driver-candidate/v1", "", "", "", 0, 0.0, 0.0, 0.0, java.util.List.of(), java.util.List.of()))
                        .rerankScore())
                .average()
                .orElse(0.0);
        double averageCompletionEta = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.projectedCompletionEtaMinutes())
                .average()
                .orElse(etaContext.maxEtaMinutes());
        double averageRouteValue = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> proposal.routeValue())
                .average()
                .orElse(0.0);
        double averageStabilityProxy = routeProposalStage.routeProposals().stream()
                .mapToDouble(proposal -> 0.6 * context.averagePairSupport(context.bundle(proposal.bundleId()).orderIds())
                        + 0.4 * context.pickupCompactness(proposal.bundleId()))
                .average()
                .orElse(0.0);
        int urgentOrderCount = (int) request.openOrders().stream().filter(order -> order.urgent()).count();
        return new PostDropShiftFeatureVector(
                "post-drop-shift-feature-vector/v1",
                request.traceId(),
                etaContext.corridorId(),
                request.openOrders().size(),
                urgentOrderCount,
                request.availableDrivers().size(),
                averageCompletionEta,
                averageRouteValue,
                averageDriverRerankScore,
                averageStabilityProxy,
                45);
    }
}

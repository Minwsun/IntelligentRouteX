package com.routechain.v2.selector;

import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteProposalSource;
import com.routechain.v2.route.RouteProposalSummary;
import com.routechain.v2.scenario.DispatchScenarioStage;
import com.routechain.v2.scenario.RobustUtility;
import com.routechain.v2.scenario.ScenarioEvaluationSummary;

import java.util.EnumMap;
import java.util.List;

final class SelectorTestFixtures {
    private SelectorTestFixtures() {
    }

    static SelectorCandidate candidate(String proposalId,
                                       String bundleId,
                                       String driverId,
                                       List<String> orderIds,
                                       double robustUtility,
                                       double routeValue,
                                       double selectionScore,
                                       boolean feasible) {
        return new SelectorCandidate(
                "selector-candidate/v1",
                proposalId,
                bundleId,
                orderIds.getFirst(),
                driverId,
                orderIds,
                robustUtility,
                routeValue,
                RouteProposalSource.HEURISTIC_FAST,
                "cluster-1",
                false,
                selectionScore,
                feasible,
                List.of("test-candidate"),
                List.of());
    }

    static SelectorCandidateEnvelope envelope(String proposalId,
                                              String bundleId,
                                              String driverId,
                                              List<String> orderIds,
                                              double robustUtility,
                                              double routeValue,
                                              double selectionScore,
                                              double projectedPickupEtaMinutes,
                                              boolean feasible) {
        return new SelectorCandidateEnvelope(
                candidate(proposalId, bundleId, driverId, orderIds, robustUtility, routeValue, selectionScore, feasible),
                projectedPickupEtaMinutes);
    }

    static RouteProposal proposal(String proposalId,
                                  String bundleId,
                                  String anchorOrderId,
                                  String driverId,
                                  RouteProposalSource source,
                                  List<String> stopOrder,
                                  double projectedPickupEtaMinutes,
                                  double projectedCompletionEtaMinutes,
                                  double routeValue,
                                  boolean feasible) {
        return new RouteProposal(
                "route-proposal/v1",
                proposalId,
                bundleId,
                anchorOrderId,
                driverId,
                source,
                stopOrder,
                projectedPickupEtaMinutes,
                projectedCompletionEtaMinutes,
                routeValue,
                feasible,
                List.of("test-route-proposal"),
                List.of());
    }

    static RobustUtility utility(String proposalId, double robustUtility) {
        return new RobustUtility(
                "robust-utility/v1",
                proposalId,
                robustUtility,
                robustUtility,
                robustUtility,
                robustUtility,
                robustUtility,
                1,
                1);
    }

    static DispatchRouteProposalStage routeProposalStage(List<RouteProposal> proposals) {
        return new DispatchRouteProposalStage(
                "dispatch-route-proposal-stage/v1",
                proposals,
                new RouteProposalSummary(
                        "route-proposal-summary/v1",
                        proposals.size(),
                        proposals.size(),
                        proposals.size(),
                        proposals.size(),
                        new EnumMap<>(RouteProposalSource.class),
                        List.of()),
                List.of());
    }

    static DispatchScenarioStage scenarioStage(List<RobustUtility> robustUtilities) {
        return new DispatchScenarioStage(
                "dispatch-scenario-stage/v1",
                List.of(),
                robustUtilities,
                ScenarioEvaluationSummary.empty(),
                List.of());
    }
}

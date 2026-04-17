package com.routechain.v2.scenario;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.context.FreshnessMetadata;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DispatchScenarioService {
    private final ScenarioGateEvaluator scenarioGateEvaluator;
    private final ScenarioEvaluator scenarioEvaluator;
    private final RobustUtilityAggregator robustUtilityAggregator;

    public DispatchScenarioService(ScenarioGateEvaluator scenarioGateEvaluator,
                                   ScenarioEvaluator scenarioEvaluator,
                                   RobustUtilityAggregator robustUtilityAggregator) {
        this.scenarioGateEvaluator = scenarioGateEvaluator;
        this.scenarioEvaluator = scenarioEvaluator;
        this.robustUtilityAggregator = robustUtilityAggregator;
    }

    public DispatchScenarioStage evaluate(DispatchV2Request request,
                                          EtaContext etaContext,
                                          FreshnessMetadata freshnessMetadata,
                                          List<LiveStageMetadata> liveStageMetadata,
                                          DispatchRouteProposalStage routeProposalStage,
                                          DispatchRouteCandidateStage routeCandidateStage,
                                          DispatchBundleStage bundleStage,
                                          DispatchPairClusterStage pairClusterStage) {
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request.availableDrivers(),
                pairClusterStage,
                bundleStage);
        Map<String, DriverCandidate> driverCandidateByKey = routeCandidateStage.driverCandidates().stream()
                .collect(java.util.stream.Collectors.toMap(
                        candidate -> key(candidate.bundleId(), candidate.anchorOrderId(), candidate.driverId()),
                        candidate -> candidate,
                        (left, right) -> left));
        List<ScenarioEvaluation> evaluations = new ArrayList<>();
        List<String> degradeReasons = new ArrayList<>();
        for (RouteProposal proposal : routeProposalStage.routeProposals()) {
            DriverCandidate driverCandidate = driverCandidateByKey.get(key(proposal.bundleId(), proposal.anchorOrderId(), proposal.driverId()));
            if (driverCandidate == null) {
                continue;
            }
            List<ScenarioGateDecision> decisions = scenarioGateEvaluator.gate(proposal, driverCandidate, context, etaContext, freshnessMetadata, liveStageMetadata);
            for (ScenarioGateDecision decision : decisions) {
                ScenarioEvaluationResult result = scenarioEvaluator.evaluate(proposal, driverCandidate, context, etaContext, decision);
                evaluations.add(result.evaluation());
                degradeReasons.addAll(result.evaluation().degradeReasons());
            }
        }
        List<RobustUtility> robustUtilities = routeProposalStage.routeProposals().stream()
                .map(RouteProposal::proposalId)
                .distinct()
                .sorted()
                .map(proposalId -> robustUtilityAggregator.aggregate(proposalId, evaluations))
                .toList();
        List<String> distinctDegradeReasons = degradeReasons.stream().distinct().toList();
        return new DispatchScenarioStage(
                "dispatch-scenario-stage/v1",
                List.copyOf(evaluations),
                robustUtilities,
                summarize(evaluations, robustUtilities, distinctDegradeReasons),
                distinctDegradeReasons);
    }

    private ScenarioEvaluationSummary summarize(List<ScenarioEvaluation> evaluations,
                                                List<RobustUtility> robustUtilities,
                                                List<String> degradeReasons) {
        Map<ScenarioType, Integer> scenarioCounts = new EnumMap<>(ScenarioType.class);
        Map<ScenarioType, Integer> appliedScenarioCounts = new EnumMap<>(ScenarioType.class);
        evaluations.forEach(evaluation -> {
            scenarioCounts.merge(evaluation.scenario(), 1, Integer::sum);
            if (evaluation.applied()) {
                appliedScenarioCounts.merge(evaluation.scenario(), 1, Integer::sum);
            }
        });
        int appliedScenarioCount = evaluations.stream().mapToInt(evaluation -> evaluation.applied() ? 1 : 0).sum();
        return new ScenarioEvaluationSummary(
                "scenario-evaluation-summary/v1",
                robustUtilities.size(),
                evaluations.size(),
                appliedScenarioCount,
                scenarioCounts,
                appliedScenarioCounts,
                degradeReasons);
    }

    private String key(String bundleId, String anchorOrderId, String driverId) {
        return bundleId + "|" + anchorOrderId + "|" + driverId;
    }
}

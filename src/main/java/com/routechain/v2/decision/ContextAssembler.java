package com.routechain.v2.decision;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.executor.DispatchExecutorStage;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.scenario.DispatchScenarioStage;
import com.routechain.v2.selector.DispatchSelectorStage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ContextAssembler {
    private final RouteChainDispatchV2Properties properties;
    private final ContextToolRegistry contextToolRegistry;

    public ContextAssembler(RouteChainDispatchV2Properties properties, ContextToolRegistry contextToolRegistry) {
        this.properties = properties;
        this.contextToolRegistry = contextToolRegistry;
    }

    public DecisionStageInputV1 observationInput(DispatchV2Request request) {
        return stageInput(
                request,
                DecisionStageName.OBSERVATION_PACK,
                Map.of(
                        "openOrderCount", request.openOrders().size(),
                        "availableDriverCount", request.availableDrivers().size(),
                        "weatherProfile", String.valueOf(request.weatherProfile()),
                        "activeMode", properties.getDecision().getMode()),
                Map.of(
                        "objective", "normalize-world-state",
                        "tools", contextToolRegistry.toolManifest()),
                List.of());
    }

    public DecisionStageInputV1 pairBundleInput(DispatchV2Request request,
                                                EtaContext etaContext,
                                                DispatchPairClusterStage pairClusterStage,
                                                DispatchBundleStage bundleStage) {
        return stageInput(
                request,
                DecisionStageName.PAIR_BUNDLE,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", bundleStage.bundleCandidates().stream().limit(12).map(bundle -> bundle.bundleId()).toList(),
                        "bundleCount", bundleStage.bundleCandidates().size(),
                        "microClusterCount", pairClusterStage.microClusters().size(),
                        "pairEdgeCount", pairClusterStage.pairSimilarityGraph().edges().size()),
                List.of("observation-pack"));
    }

    public DecisionStageInputV1 anchorInput(DispatchV2Request request,
                                            EtaContext etaContext,
                                            DispatchRouteCandidateStage routeCandidateStage) {
        return stageInput(
                request,
                DecisionStageName.ANCHOR,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", routeCandidateStage.pickupAnchors().stream().limit(12).map(anchor -> anchor.anchorOrderId()).toList(),
                        "anchorCount", routeCandidateStage.pickupAnchors().size()),
                List.of("pair-bundle"));
    }

    public DecisionStageInputV1 driverInput(DispatchV2Request request,
                                            EtaContext etaContext,
                                            DispatchRouteCandidateStage routeCandidateStage) {
        return stageInput(
                request,
                DecisionStageName.DRIVER,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", routeCandidateStage.driverCandidates().stream().limit(12).map(candidate -> candidate.driverId()).toList(),
                        "driverCandidateCount", routeCandidateStage.driverCandidates().size()),
                List.of("anchor"));
    }

    public DecisionStageInputV1 routeGenerationInput(DispatchV2Request request,
                                                     EtaContext etaContext,
                                                     DispatchRouteProposalStage routeProposalStage) {
        return stageInput(
                request,
                DecisionStageName.ROUTE_GENERATION,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", routeProposalStage.routeProposals().stream().limit(8).map(proposal -> proposal.proposalId()).toList(),
                        "proposalCount", routeProposalStage.routeProposals().size()),
                List.of("driver"));
    }

    public DecisionStageInputV1 routeCritiqueInput(DispatchV2Request request,
                                                   EtaContext etaContext,
                                                   DispatchRouteProposalStage routeProposalStage) {
        return stageInput(
                request,
                DecisionStageName.ROUTE_CRITIQUE,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", routeProposalStage.routeProposals().stream().limit(8).map(proposal -> proposal.proposalId()).toList(),
                        "proposalCount", routeProposalStage.routeProposals().size(),
                        "geometryAvailableCount", routeProposalStage.routeProposals().stream().filter(proposal -> proposal.geometryAvailable()).count()),
                List.of("route-generation"));
    }

    public DecisionStageInputV1 scenarioInput(DispatchV2Request request,
                                              EtaContext etaContext,
                                              DispatchScenarioStage scenarioStage) {
        return stageInput(
                request,
                DecisionStageName.SCENARIO,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", scenarioStage.robustUtilities().stream().limit(8).map(utility -> utility.proposalId()).toList(),
                        "robustUtilityCount", scenarioStage.robustUtilities().size(),
                        "scenarioEvaluationCount", scenarioStage.scenarioEvaluations().size()),
                List.of("route-critique"));
    }

    public DecisionStageInputV1 finalSelectionInput(DispatchV2Request request,
                                                    EtaContext etaContext,
                                                    DispatchSelectorStage selectorStage) {
        return stageInput(
                request,
                DecisionStageName.FINAL_SELECTION,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", selectorStage.selectorCandidates().stream().limit(8).map(candidate -> candidate.proposalId()).toList(),
                        "selectorCandidateCount", selectorStage.selectorCandidates().size(),
                        "selectedProposalIds", selectorStage.globalSelectionResult().selectedProposals().stream()
                                .map(selectedProposal -> selectedProposal.proposalId())
                                .toList()),
                List.of("scenario"));
    }

    public DecisionStageInputV1 safetyExecuteInput(DispatchV2Request request,
                                                   EtaContext etaContext,
                                                   DispatchExecutorStage executorStage) {
        return stageInput(
                request,
                DecisionStageName.SAFETY_EXECUTE,
                dispatchContext(request, etaContext),
                Map.of(
                        "topIds", executorStage.assignments().stream().map(assignment -> assignment.assignmentId()).toList(),
                        "assignmentCount", executorStage.assignments().size()),
                List.of("final-selection"));
    }

    private DecisionStageInputV1 stageInput(DispatchV2Request request,
                                            DecisionStageName stageName,
                                            Map<String, Object> dispatchContext,
                                            Map<String, Object> candidateSet,
                                            List<String> upstreamRefs) {
        return new DecisionStageInputV1(
                "stage-input-v1",
                request.traceId(),
                request.traceId(),
                tickId(request.decisionTime()),
                stageName,
                dispatchContext,
                candidateSet,
                Map.of(
                        "strictStructuredOutputs", properties.getDecision().getLlm().isStrictStructuredOutputs(),
                        "parallelToolCalls", properties.getDecision().getLlm().isParallelToolCalls()),
                Map.of(
                        "correctness", 0.4,
                        "latency", 0.2,
                        "robustness", 0.25,
                        "conflict_free", 0.15),
                upstreamRefs);
    }

    private Map<String, Object> dispatchContext(DispatchV2Request request, EtaContext etaContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("decisionTime", request.decisionTime());
        context.put("weatherProfile", String.valueOf(request.weatherProfile()));
        context.put("baselineEtaMinutes", etaContext.averageEtaMinutes());
        context.put("liveEtaMinutes", etaContext.maxEtaMinutes());
        context.put("uncertainty", etaContext.averageUncertainty());
        context.put("trafficBad", etaContext.trafficBadSignal());
        context.put("weatherBad", etaContext.weatherBadSignal());
        context.put("corridorSignature", etaContext.corridorId());
        return context;
    }

    private String tickId(Instant decisionTime) {
        return decisionTime == null ? "tick-unknown" : decisionTime.toString();
    }
}

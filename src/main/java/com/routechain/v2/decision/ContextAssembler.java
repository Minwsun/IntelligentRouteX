package com.routechain.v2.decision;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.executor.DispatchExecutorStage;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.PickupAnchor;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.scenario.DispatchScenarioStage;
import com.routechain.v2.scenario.RobustUtility;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectorCandidate;

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
                        "activeMode", properties.getDecision().getMode(),
                        "regionCount", request.regions().size()),
                Map.of(
                        "topIds", List.of(),
                        "window", List.of()),
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
                        "window", bundleStage.bundleCandidates().stream().limit(12).map(this::bundleWindowRow).toList(),
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
                        "topIds", routeCandidateStage.pickupAnchors().stream().limit(6).map(anchor -> anchor.anchorOrderId()).toList(),
                        "window", routeCandidateStage.pickupAnchors().stream().limit(6).map(this::anchorWindowRow).toList(),
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
                        "topIds", routeCandidateStage.driverCandidates().stream().limit(8).map(candidate -> candidate.driverId()).toList(),
                        "window", routeCandidateStage.driverCandidates().stream().limit(8).map(this::driverWindowRow).toList(),
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
                        "topIds", routeProposalStage.routeProposals().stream().limit(4).map(proposal -> proposal.proposalId()).toList(),
                        "window", routeProposalStage.routeProposals().stream().limit(4).map(this::routeWindowRow).toList(),
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
                        "topIds", routeProposalStage.routeProposals().stream().limit(4).map(proposal -> proposal.proposalId()).toList(),
                        "window", routeProposalStage.routeProposals().stream().limit(4).map(this::routeWindowRow).toList(),
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
                        "topIds", scenarioStage.robustUtilities().stream().limit(3).map(utility -> utility.proposalId()).toList(),
                        "window", scenarioStage.robustUtilities().stream().limit(3).map(this::scenarioWindowRow).toList(),
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
                        "topIds", selectorStage.selectorCandidates().stream().limit(3).map(candidate -> candidate.proposalId()).toList(),
                        "window", selectorStage.selectorCandidates().stream().limit(3).map(this::selectorWindowRow).toList(),
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
                        "staticPrefix", staticPrefix(stageName),
                        "schemaContract", "stage_output_v1",
                        "contextBudget", stageBudget(stageName),
                        "strictStructuredOutputs", properties.getDecision().getLlm().isStrictStructuredOutputs(),
                        "parallelToolCalls", properties.getDecision().getLlm().isParallelToolCalls(),
                        "toolManifest", contextToolRegistry.toolManifest(stageName)),
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
        context.put("decisionMode", properties.getDecision().getMode());
        context.put("authoritativeStages", properties.getDecision().getAuthoritativeStages());
        return context;
    }

    private Map<String, Object> bundleWindowRow(BundleCandidate bundle) {
        return Map.of(
                "bundleId", bundle.bundleId(),
                "family", bundle.family().name(),
                "orderCount", bundle.orderIds().size(),
                "boundaryCross", bundle.boundaryCross(),
                "score", bundle.score(),
                "feasible", bundle.feasible());
    }

    private Map<String, Object> anchorWindowRow(PickupAnchor anchor) {
        return Map.of(
                "anchorOrderId", anchor.anchorOrderId(),
                "bundleId", anchor.bundleId(),
                "anchorRank", anchor.anchorRank(),
                "score", anchor.score());
    }

    private Map<String, Object> driverWindowRow(DriverCandidate driverCandidate) {
        return Map.of(
                "driverId", driverCandidate.driverId(),
                "bundleId", driverCandidate.bundleId(),
                "anchorOrderId", driverCandidate.anchorOrderId(),
                "pickupEtaMinutes", driverCandidate.pickupEtaMinutes(),
                "driverFitScore", driverCandidate.driverFitScore(),
                "rerankScore", driverCandidate.rerankScore());
    }

    private Map<String, Object> routeWindowRow(RouteProposal proposal) {
        return Map.of(
                "proposalId", proposal.proposalId(),
                "bundleId", proposal.bundleId(),
                "driverId", proposal.driverId(),
                "routeValue", proposal.routeValue(),
                "projectedPickupEtaMinutes", proposal.projectedPickupEtaMinutes(),
                "projectedCompletionEtaMinutes", proposal.projectedCompletionEtaMinutes(),
                "geometryAvailable", proposal.geometryAvailable(),
                "routeCost", proposal.routeCost(),
                "congestionScore", proposal.congestionScore());
    }

    private Map<String, Object> scenarioWindowRow(RobustUtility robustUtility) {
        return Map.of(
                "proposalId", robustUtility.proposalId(),
                "expectedValue", robustUtility.expectedValue(),
                "worstCaseValue", robustUtility.worstCaseValue(),
                "landingValue", robustUtility.landingValue(),
                "stabilityScore", robustUtility.stabilityScore(),
                "robustUtility", robustUtility.robustUtility());
    }

    private Map<String, Object> selectorWindowRow(SelectorCandidate candidate) {
        return Map.of(
                "proposalId", candidate.proposalId(),
                "bundleId", candidate.bundleId(),
                "driverId", candidate.driverId(),
                "selectionScore", candidate.selectionScore(),
                "robustUtility", candidate.robustUtility(),
                "routeValue", candidate.routeValue(),
                "feasible", candidate.feasible());
    }

    private String staticPrefix(DecisionStageName stageName) {
        return switch (stageName) {
            case OBSERVATION_PACK -> "Normalize world state. Do not invent entities.";
            case PAIR_BUNDLE -> "Rank pair and bundle candidates under hard dispatch constraints.";
            case ANCHOR -> "Choose stable pickup anchors with minimal wait and low constraint risk.";
            case DRIVER -> "Prefer driver fit, ETA discipline, and operational stability.";
            case ROUTE_GENERATION -> "Generate compact route choices without violating stop order constraints.";
            case ROUTE_CRITIQUE -> "Critique route options using route-vector realism and robustness signals.";
            case SCENARIO -> "Score robustness across weather, traffic, and forecast stress.";
            case FINAL_SELECTION -> "Select the safest high-value proposals with conflict-free bias.";
            case SAFETY_EXECUTE -> "Safety and execution are deterministic; only summarize the selected assignments.";
        };
    }

    private Map<String, Object> stageBudget(DecisionStageName stageName) {
        return switch (stageName) {
            case PAIR_BUNDLE -> Map.of("pairs", 12, "bundles", 12);
            case ANCHOR -> Map.of("bundles", 6, "anchorsPerBundle", 4);
            case DRIVER -> Map.of("bundles", 4, "driversPerBundle", 8);
            case ROUTE_GENERATION -> Map.of("bundles", 1, "drivers", 3, "alternatives", 4);
            case ROUTE_CRITIQUE -> Map.of("routes", 4);
            case SCENARIO -> Map.of("proposals", 3);
            case FINAL_SELECTION -> Map.of("proposals", 3);
            case OBSERVATION_PACK, SAFETY_EXECUTE -> Map.of("rows", 0);
        };
    }

    private String tickId(Instant decisionTime) {
        return decisionTime == null ? "tick-unknown" : decisionTime.toString();
    }
}

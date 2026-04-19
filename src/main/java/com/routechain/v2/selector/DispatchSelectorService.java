package com.routechain.v2.selector;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.harvest.emitters.DispatchHarvestService;
import com.routechain.v2.harvest.writers.NoOpHarvestWriter;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.scenario.DispatchScenarioStage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class DispatchSelectorService {
    private final SelectorCandidateBuilder selectorCandidateBuilder;
    private final ConflictGraphBuilder conflictGraphBuilder;
    private final GlobalSelector globalSelector;
    private final DispatchHarvestService dispatchHarvestService;

    public DispatchSelectorService(SelectorCandidateBuilder selectorCandidateBuilder,
                                   ConflictGraphBuilder conflictGraphBuilder,
                                   GlobalSelector globalSelector) {
        this(
                selectorCandidateBuilder,
                conflictGraphBuilder,
                globalSelector,
                new DispatchHarvestService(com.routechain.config.RouteChainDispatchV2Properties.defaults().getHarvest(), new NoOpHarvestWriter()));
    }

    public DispatchSelectorService(SelectorCandidateBuilder selectorCandidateBuilder,
                                   ConflictGraphBuilder conflictGraphBuilder,
                                   GlobalSelector globalSelector,
                                   DispatchHarvestService dispatchHarvestService) {
        this.selectorCandidateBuilder = selectorCandidateBuilder;
        this.conflictGraphBuilder = conflictGraphBuilder;
        this.globalSelector = globalSelector;
        this.dispatchHarvestService = dispatchHarvestService;
    }

    public DispatchSelectorStage evaluate(DispatchV2Request request,
                                         EtaContext etaContext,
                                         DispatchPairClusterStage pairClusterStage,
                                         DispatchBundleStage bundleStage,
                                         DispatchRouteCandidateStage routeCandidateStage,
                                         DispatchRouteProposalStage routeProposalStage,
                                         DispatchScenarioStage scenarioStage) {
        long selectorStartedAt = System.nanoTime();
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request.availableDrivers(),
                pairClusterStage,
                bundleStage);
        SelectorCandidateBuildResult buildResult = selectorCandidateBuilder.build(
                routeProposalStage,
                scenarioStage,
                routeCandidateStage,
                context);
        List<SelectorCandidate> selectorCandidates = buildResult.candidateEnvelopes().stream()
                .map(SelectorCandidateEnvelope::candidate)
                .toList();
        ConflictGraph conflictGraph = conflictGraphBuilder.build(selectorCandidates);
        SelectorSelectionOutcome selectionOutcome = globalSelector.select(buildResult.candidateEnvelopes(), conflictGraph);
        buildResult.decisionTrace().merge(selectionOutcome.decisionTrace());
        List<String> degradeReasons = Stream.concat(
                        buildResult.degradeReasons().stream(),
                        selectionOutcome.selectionResult().degradeReasons().stream())
                .distinct()
                .toList();
        GlobalSelectionResult selectionResult = selectionOutcome.selectionResult().degradeReasons().equals(degradeReasons)
                ? selectionOutcome.selectionResult()
                : new GlobalSelectionResult(
                selectionOutcome.selectionResult().schemaVersion(),
                selectionOutcome.selectionResult().selectedProposals(),
                selectionOutcome.selectionResult().retainedCandidateCount(),
                selectionOutcome.selectionResult().selectedCount(),
                selectionOutcome.selectionResult().solverMode(),
                selectionOutcome.selectionResult().objectiveValue(),
                degradeReasons);
        emitSelectorCandidates(request, selectorCandidates, conflictGraph, selectionResult);
        return new DispatchSelectorStage(
                "dispatch-selector-stage/v1",
                selectorCandidates,
                conflictGraph,
                selectionResult,
                summarize(selectorCandidates, conflictGraph, selectionResult, degradeReasons),
                List.of(DispatchStageLatency.measured("global-selector", elapsedMs(selectorStartedAt), false)),
                degradeReasons);
    }

    private GlobalSelectorSummary summarize(List<SelectorCandidate> selectorCandidates,
                                            ConflictGraph conflictGraph,
                                            GlobalSelectionResult selectionResult,
                                            List<String> degradeReasons) {
        int feasibleCandidateCount = (int) selectorCandidates.stream().filter(SelectorCandidate::feasible).count();
        return new GlobalSelectorSummary(
                "global-selector-summary/v1",
                selectorCandidates.size(),
                feasibleCandidateCount,
                conflictGraph.conflictEdgeCount(),
                selectionResult.selectedCount(),
                selectionResult.solverMode(),
                degradeReasons);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void emitSelectorCandidates(DispatchV2Request request,
                                        List<SelectorCandidate> selectorCandidates,
                                        ConflictGraph conflictGraph,
                                        GlobalSelectionResult selectionResult) {
        java.util.Set<String> selectedIds = selectionResult.selectedProposals().stream()
                .map(SelectedProposal::proposalId)
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (SelectorCandidate candidate : selectorCandidates) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("proposalId", candidate.proposalId());
            payload.put("bundleId", candidate.bundleId());
            payload.put("driverId", candidate.driverId());
            payload.put("orderIds", candidate.orderIds());
            payload.put("routeValue", candidate.routeValue());
            payload.put("bundleSupportScore", null);
            payload.put("driverFitScore", null);
            payload.put("scenarioRobustValue", candidate.robustUtility());
            payload.put("selectionScore", candidate.selectionScore());
            payload.put("conflictSummary", conflictGraph.conflictEdgeCount());
            payload.put("selected", selectedIds.contains(candidate.proposalId()));
            payload.put("skipReason", selectedIds.contains(candidate.proposalId()) ? "" : "not-selected-by-global-selector");
            payload.put("replaceReason", "");
            payloads.add(payload);
        }
        dispatchHarvestService.writeRecords("selector-candidate", "global-selector", request, payloads);
    }
}

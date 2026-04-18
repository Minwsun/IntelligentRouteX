package com.routechain.v2.selector;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.scenario.DispatchScenarioStage;

import java.util.List;
import java.util.stream.Stream;

public final class DispatchSelectorService {
    private final SelectorCandidateBuilder selectorCandidateBuilder;
    private final ConflictGraphBuilder conflictGraphBuilder;
    private final GlobalSelector globalSelector;

    public DispatchSelectorService(SelectorCandidateBuilder selectorCandidateBuilder,
                                   ConflictGraphBuilder conflictGraphBuilder,
                                   GlobalSelector globalSelector) {
        this.selectorCandidateBuilder = selectorCandidateBuilder;
        this.conflictGraphBuilder = conflictGraphBuilder;
        this.globalSelector = globalSelector;
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
}

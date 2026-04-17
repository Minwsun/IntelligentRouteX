package com.routechain.v2;

import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.bundle.DispatchBundleStageService;
import com.routechain.v2.route.DispatchRouteCandidateService;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalService;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.scenario.DispatchScenarioService;
import com.routechain.v2.scenario.DispatchScenarioStage;
import com.routechain.v2.executor.DispatchExecutorService;
import com.routechain.v2.executor.DispatchExecutorStage;
import com.routechain.v2.selector.DispatchSelectorService;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.cluster.DispatchPairClusterService;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.DispatchEtaContextStage;
import com.routechain.v2.feedback.DecisionLogService;
import com.routechain.v2.feedback.DispatchReplayRecorder;
import com.routechain.v2.feedback.HotStartManager;
import com.routechain.v2.feedback.SnapshotService;
import com.routechain.v2.feedback.SnapshotWriteResult;
import com.routechain.v2.feedback.WarmStartManager;

public final class DispatchV2Core {
    private final DispatchEtaContextService dispatchEtaContextService;
    private final DispatchPairClusterService dispatchPairClusterService;
    private final DispatchBundleStageService dispatchBundleStageService;
    private final DispatchRouteCandidateService dispatchRouteCandidateService;
    private final DispatchRouteProposalService dispatchRouteProposalService;
    private final DispatchScenarioService dispatchScenarioService;
    private final DispatchSelectorService dispatchSelectorService;
    private final DispatchExecutorService dispatchExecutorService;
    private final DispatchReplayRecorder dispatchReplayRecorder;
    private final DecisionLogService decisionLogService;
    private final SnapshotService snapshotService;
    private final WarmStartManager warmStartManager;
    private final HotStartManager hotStartManager;

    public DispatchV2Core(DispatchEtaContextService dispatchEtaContextService,
                          DispatchPairClusterService dispatchPairClusterService,
                          DispatchBundleStageService dispatchBundleStageService,
                          DispatchRouteCandidateService dispatchRouteCandidateService,
                          DispatchRouteProposalService dispatchRouteProposalService,
                          DispatchScenarioService dispatchScenarioService,
                          DispatchSelectorService dispatchSelectorService,
                          DispatchExecutorService dispatchExecutorService,
                          DispatchReplayRecorder dispatchReplayRecorder,
                          DecisionLogService decisionLogService,
                          SnapshotService snapshotService,
                          WarmStartManager warmStartManager,
                          HotStartManager hotStartManager) {
        this.dispatchEtaContextService = dispatchEtaContextService;
        this.dispatchPairClusterService = dispatchPairClusterService;
        this.dispatchBundleStageService = dispatchBundleStageService;
        this.dispatchRouteCandidateService = dispatchRouteCandidateService;
        this.dispatchRouteProposalService = dispatchRouteProposalService;
        this.dispatchScenarioService = dispatchScenarioService;
        this.dispatchSelectorService = dispatchSelectorService;
        this.dispatchExecutorService = dispatchExecutorService;
        this.dispatchReplayRecorder = dispatchReplayRecorder;
        this.decisionLogService = decisionLogService;
        this.snapshotService = snapshotService;
        this.warmStartManager = warmStartManager;
        this.hotStartManager = hotStartManager;
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        DispatchEtaContextStage etaStage = dispatchEtaContextService.evaluate(request);
        DispatchPairClusterStage pairClusterStage = dispatchPairClusterService.evaluate(request, etaStage.etaContext());
        DispatchBundleStage bundleStage = dispatchBundleStageService.evaluate(etaStage.etaContext(), pairClusterStage);
        DispatchRouteCandidateStage routeCandidateStage = dispatchRouteCandidateService.evaluate(request, etaStage.etaContext(), pairClusterStage, bundleStage);
        DispatchRouteProposalStage routeProposalStage = dispatchRouteProposalService.evaluate(
                request,
                etaStage.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage);
        DispatchScenarioStage scenarioStage = dispatchScenarioService.evaluate(
                request,
                etaStage.etaContext(),
                routeProposalStage,
                routeCandidateStage,
                bundleStage,
                pairClusterStage);
        DispatchSelectorStage selectorStage = dispatchSelectorService.evaluate(
                request,
                etaStage.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                scenarioStage);
        DispatchExecutorStage executorStage = dispatchExecutorService.evaluate(
                request,
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                selectorStage);
        java.util.List<String> degradeReasons = java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(
                                java.util.stream.Stream.concat(
                                        java.util.stream.Stream.concat(
                                                java.util.stream.Stream.concat(
                                                        java.util.stream.Stream.concat(
                                                                etaStage.degradeReasons().stream(),
                                                                pairClusterStage.degradeReasons().stream()),
                                                        bundleStage.degradeReasons().stream()),
                                                routeCandidateStage.degradeReasons().stream()),
                                        routeProposalStage.degradeReasons().stream()),
                                scenarioStage.degradeReasons().stream()),
                        java.util.stream.Stream.concat(
                                selectorStage.degradeReasons().stream(),
                                executorStage.degradeReasons().stream()))
                .distinct()
                .toList();
        DispatchV2Result pipelineResult = new DispatchV2Result(
                "dispatch-v2-result/v1",
                request.traceId(),
                false,
                null,
                java.util.List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"),
                etaStage.etaContext(),
                etaStage.etaStageTrace(),
                etaStage.freshnessMetadata(),
                pairClusterStage.bufferedOrderWindow(),
                pairClusterStage.pairGraphSummary(),
                pairClusterStage.microClusters(),
                pairClusterStage.microClusterSummary(),
                bundleStage.boundaryExpansions(),
                bundleStage.boundaryExpansionSummary(),
                bundleStage.bundleCandidates(),
                bundleStage.bundlePoolSummary(),
                routeCandidateStage.pickupAnchors(),
                routeCandidateStage.pickupAnchorSummary(),
                routeCandidateStage.driverCandidates(),
                routeCandidateStage.driverShortlistSummary(),
                routeProposalStage.routeProposals(),
                routeProposalStage.routeProposalSummary(),
                scenarioStage.scenarioEvaluations(),
                scenarioStage.robustUtilities(),
                scenarioStage.scenarioEvaluationSummary(),
                selectorStage.selectorCandidates(),
                selectorStage.conflictGraph(),
                selectorStage.globalSelectionResult(),
                selectorStage.globalSelectorSummary(),
                executorStage.assignments(),
                executorStage.dispatchExecutionSummary(),
                warmStartManager.currentState(),
                HotStartState.empty(),
                degradeReasons);
        dispatchReplayRecorder.record(request);
        decisionLogService.write(request, pipelineResult);
        SnapshotWriteResult snapshotWriteResult = snapshotService.save(request, pipelineResult);
        HotStartState hotStartState = hotStartManager.update(snapshotWriteResult.snapshot());
        return new DispatchV2Result(
                pipelineResult.schemaVersion(),
                pipelineResult.traceId(),
                pipelineResult.fallbackUsed(),
                pipelineResult.selectedRouteId(),
                pipelineResult.decisionStages(),
                pipelineResult.etaContext(),
                pipelineResult.etaStageTrace(),
                pipelineResult.freshnessMetadata(),
                pipelineResult.bufferedOrderWindow(),
                pipelineResult.pairGraphSummary(),
                pipelineResult.microClusters(),
                pipelineResult.microClusterSummary(),
                pipelineResult.boundaryExpansions(),
                pipelineResult.boundaryExpansionSummary(),
                pipelineResult.bundleCandidates(),
                pipelineResult.bundlePoolSummary(),
                pipelineResult.pickupAnchors(),
                pipelineResult.pickupAnchorSummary(),
                pipelineResult.driverCandidates(),
                pipelineResult.driverShortlistSummary(),
                pipelineResult.routeProposals(),
                pipelineResult.routeProposalSummary(),
                pipelineResult.scenarioEvaluations(),
                pipelineResult.robustUtilities(),
                pipelineResult.scenarioEvaluationSummary(),
                pipelineResult.selectorCandidates(),
                pipelineResult.conflictGraph(),
                pipelineResult.globalSelectionResult(),
                pipelineResult.globalSelectorSummary(),
                pipelineResult.assignments(),
                pipelineResult.dispatchExecutionSummary(),
                pipelineResult.warmStartState(),
                hotStartState,
                pipelineResult.degradeReasons());
    }
}

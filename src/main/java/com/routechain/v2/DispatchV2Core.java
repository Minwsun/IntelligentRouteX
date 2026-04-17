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
import com.routechain.v2.feedback.HotStartAppliedReuse;
import com.routechain.v2.feedback.HotStartReusePlan;
import com.routechain.v2.feedback.PostDispatchHardeningService;
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
    private final WarmStartManager warmStartManager;
    private final PostDispatchHardeningService postDispatchHardeningService;

    public DispatchV2Core(DispatchEtaContextService dispatchEtaContextService,
                          DispatchPairClusterService dispatchPairClusterService,
                          DispatchBundleStageService dispatchBundleStageService,
                          DispatchRouteCandidateService dispatchRouteCandidateService,
                          DispatchRouteProposalService dispatchRouteProposalService,
                          DispatchScenarioService dispatchScenarioService,
                          DispatchSelectorService dispatchSelectorService,
                          DispatchExecutorService dispatchExecutorService,
                          WarmStartManager warmStartManager,
                          PostDispatchHardeningService postDispatchHardeningService) {
        this.dispatchEtaContextService = dispatchEtaContextService;
        this.dispatchPairClusterService = dispatchPairClusterService;
        this.dispatchBundleStageService = dispatchBundleStageService;
        this.dispatchRouteCandidateService = dispatchRouteCandidateService;
        this.dispatchRouteProposalService = dispatchRouteProposalService;
        this.dispatchScenarioService = dispatchScenarioService;
        this.dispatchSelectorService = dispatchSelectorService;
        this.dispatchExecutorService = dispatchExecutorService;
        this.warmStartManager = warmStartManager;
        this.postDispatchHardeningService = postDispatchHardeningService;
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        DispatchPipelineExecution execution = executePipeline(request, true);
        HotStartAppliedReuse appliedReuse = new HotStartAppliedReuse(
                "hot-start-applied-reuse/v1",
                execution.pairClusterStage().hotStartReuseSummary().reused(),
                execution.bundleStage().hotStartReuseSummary().reused(),
                execution.routeProposalStage().hotStartReuseSummary().reused(),
                execution.bundleStage().hotStartReuseSummary().reusedCount(),
                execution.routeProposalStage().hotStartReuseSummary().reusedCount(),
                java.util.stream.Stream.of(
                                execution.pairClusterStage().hotStartReuseSummary().degradeReasons().stream(),
                                execution.bundleStage().hotStartReuseSummary().degradeReasons().stream(),
                                execution.routeProposalStage().hotStartReuseSummary().degradeReasons().stream())
                        .flatMap(stream -> stream)
                        .distinct()
                        .toList());
        return postDispatchHardeningService.apply(
                request,
                execution,
                execution.hotStartReusePlan(),
                appliedReuse);
    }

    public DispatchV2Result dispatchForReplay(DispatchV2Request request) {
        return executePipeline(request, false).result();
    }

    private DispatchPipelineExecution executePipeline(DispatchV2Request request, boolean allowHotStartReuse) {
        DispatchEtaContextStage etaStage = dispatchEtaContextService.evaluate(request);
        HotStartReusePlan reusePlan = allowHotStartReuse
                ? postDispatchHardeningService.planHotStartReuse(etaStage.etaContext())
                : HotStartReusePlan.none();
        DispatchPairClusterStage pairClusterStage = dispatchPairClusterService.evaluate(
                request,
                etaStage.etaContext(),
                reusePlan.pairClusterReuseInput());
        DispatchBundleStage bundleStage = dispatchBundleStageService.evaluate(
                etaStage.etaContext(),
                pairClusterStage,
                reusePlan.bundleReuseInput());
        DispatchRouteCandidateStage routeCandidateStage = dispatchRouteCandidateService.evaluate(request, etaStage.etaContext(), pairClusterStage, bundleStage);
        DispatchRouteProposalStage routeProposalStage = dispatchRouteProposalService.evaluate(
                request,
                etaStage.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                reusePlan.routeProposalReuseInput());
        DispatchScenarioStage scenarioStage = dispatchScenarioService.evaluate(
                request,
                etaStage.etaContext(),
                etaStage.freshnessMetadata(),
                etaStage.liveStageMetadata(),
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
        java.util.List<MlStageMetadata> mlStageMetadata = java.util.stream.Stream.of(
                        etaStage.mlStageMetadata().stream(),
                        pairClusterStage.mlStageMetadata().stream(),
                        bundleStage.mlStageMetadata().stream(),
                        routeCandidateStage.mlStageMetadata().stream(),
                        routeProposalStage.mlStageMetadata().stream(),
                        scenarioStage.mlStageMetadata().stream())
                .flatMap(stream -> stream)
                .distinct()
                .toList();
        java.util.List<LiveStageMetadata> liveStageMetadata = java.util.stream.Stream.of(
                        etaStage.liveStageMetadata().stream())
                .flatMap(stream -> stream)
                .distinct()
                .toList();
        DispatchV2Result result = new DispatchV2Result(
                "dispatch-v2-result/v1",
                request.traceId(),
                false,
                null,
                java.util.List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"),
                etaStage.etaContext(),
                etaStage.etaStageTrace(),
                scenarioStage.freshnessMetadata(),
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
                mlStageMetadata,
                liveStageMetadata,
                selectorStage.selectorCandidates(),
                selectorStage.conflictGraph(),
                selectorStage.globalSelectionResult(),
                selectorStage.globalSelectorSummary(),
                executorStage.assignments(),
                executorStage.dispatchExecutionSummary(),
                warmStartManager.currentState(),
                new HotStartState(
                        "hot-start-state/v2",
                        reusePlan.previousTraceId(),
                        reusePlan.reuseState() == null ? java.util.List.of() : reusePlan.reuseState().clusterSignatures(),
                        reusePlan.reuseState() == null ? java.util.List.of() : reusePlan.reuseState().bundleSignatures(),
                        reusePlan.reuseState() == null ? java.util.List.of() : reusePlan.reuseState().routeProposals().stream()
                                .map(proposal -> proposal.proposalId()
                                        + "|" + proposal.bundleId()
                                        + "|" + proposal.driverId()
                                        + "|" + String.join(",", proposal.stopOrder()))
                                .sorted()
                                .toList(),
                        java.util.List.of(),
                        reusePlan.reuseEligible(),
                        false,
                        false,
                        false,
                        0,
                        0,
                        reusePlan.degradeReasons()),
                degradeReasons);
        return new DispatchPipelineExecution(
                result,
                reusePlan,
                etaStage,
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                scenarioStage);
    }
}

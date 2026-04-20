package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.bundle.DispatchBundleStageService;
import com.routechain.v2.cluster.DispatchPairClusterService;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.DispatchEtaContextStage;
import com.routechain.v2.decision.ContextAssembler;
import com.routechain.v2.decision.DecisionBrainResolver;
import com.routechain.v2.decision.DecisionStageInputV1;
import com.routechain.v2.decision.DecisionStageLogger;
import com.routechain.v2.decision.DecisionStageOutputV1;
import com.routechain.v2.decision.DecisionUsageRecord;
import com.routechain.v2.decision.ResolvedDecisionBrain;
import com.routechain.v2.executor.DispatchExecutorService;
import com.routechain.v2.executor.DispatchExecutorStage;
import com.routechain.v2.feedback.DispatchRuntimeReuseState;
import com.routechain.v2.feedback.HotStartAppliedReuse;
import com.routechain.v2.feedback.HotStartReusePlan;
import com.routechain.v2.feedback.PostDispatchHardeningService;
import com.routechain.v2.feedback.WarmStartManager;
import com.routechain.v2.route.DispatchRouteCandidateService;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalService;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.scenario.DispatchScenarioService;
import com.routechain.v2.scenario.DispatchScenarioStage;
import com.routechain.v2.selector.DispatchSelectorService;
import com.routechain.v2.selector.DispatchSelectorStage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DispatchV2Core {
    private static final List<String> DECISION_STAGES = List.of(
            "eta/context",
            "order-buffer",
            "pair-graph",
            "micro-cluster",
            "boundary-expansion",
            "bundle-pool",
            "pickup-anchor",
            "driver-shortlist/rerank",
            "route-proposal-pool",
            "scenario-evaluation",
            "global-selector",
            "dispatch-executor");

    private final RouteChainDispatchV2Properties properties;
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
    private final DecisionBrainResolver decisionBrainResolver;
    private final ContextAssembler contextAssembler;
    private final DecisionStageLogger decisionStageLogger;

    public DispatchV2Core(RouteChainDispatchV2Properties properties,
                          DispatchEtaContextService dispatchEtaContextService,
                          DispatchPairClusterService dispatchPairClusterService,
                          DispatchBundleStageService dispatchBundleStageService,
                          DispatchRouteCandidateService dispatchRouteCandidateService,
                          DispatchRouteProposalService dispatchRouteProposalService,
                          DispatchScenarioService dispatchScenarioService,
                          DispatchSelectorService dispatchSelectorService,
                          DispatchExecutorService dispatchExecutorService,
                          WarmStartManager warmStartManager,
                          PostDispatchHardeningService postDispatchHardeningService,
                          DecisionBrainResolver decisionBrainResolver,
                          ContextAssembler contextAssembler,
                          DecisionStageLogger decisionStageLogger) {
        this.properties = properties;
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
        this.decisionBrainResolver = decisionBrainResolver;
        this.contextAssembler = contextAssembler;
        this.decisionStageLogger = decisionStageLogger;
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        DispatchPipelineExecution execution = executePipeline(request, true);
        DispatchLatencyBudgetSummary latencyBudgetSummary = execution.result().latencyBudgetSummary();
        List<String> reusedStageNames = execution.result().stageLatencies().stream()
                .filter(DispatchStageLatency::hotStartReused)
                .map(DispatchStageLatency::stageName)
                .toList();
        HotStartAppliedReuse appliedReuse = new HotStartAppliedReuse(
                "hot-start-applied-reuse/v1",
                execution.pairClusterStage().hotStartReuseSummary().reused(),
                execution.bundleStage().hotStartReuseSummary().reused(),
                execution.routeProposalStage().hotStartReuseSummary().reused(),
                execution.bundleStage().hotStartReuseSummary().reusedCount(),
                execution.routeProposalStage().hotStartReuseSummary().reusedCount(),
                latencyBudgetSummary.estimatedHotStartSavedMs(),
                reusedStageNames,
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
        long dispatchStartedAt = System.nanoTime();
        ResolvedDecisionBrain resolvedDecisionBrain = decisionBrainResolver.resolve();
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.observationInput(request));
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
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.pairBundleInput(request, etaStage.etaContext(), pairClusterStage, bundleStage));
        DispatchRouteCandidateStage routeCandidateStage = dispatchRouteCandidateService.evaluate(
                request,
                etaStage.etaContext(),
                pairClusterStage,
                bundleStage);
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.anchorInput(request, etaStage.etaContext(), routeCandidateStage));
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.driverInput(request, etaStage.etaContext(), routeCandidateStage));
        DispatchRouteProposalStage routeProposalStage = dispatchRouteProposalService.evaluate(
                request,
                etaStage.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                reusePlan.routeProposalReuseInput());
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.routeGenerationInput(request, etaStage.etaContext(), routeProposalStage));
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.routeCritiqueInput(request, etaStage.etaContext(), routeProposalStage));
        DispatchScenarioStage scenarioStage = dispatchScenarioService.evaluate(
                request,
                etaStage.etaContext(),
                etaStage.freshnessMetadata(),
                etaStage.liveStageMetadata(),
                routeProposalStage,
                routeCandidateStage,
                bundleStage,
                pairClusterStage);
        runDecisionSidecar(resolvedDecisionBrain, contextAssembler.scenarioInput(request, etaStage.etaContext(), scenarioStage));
        DispatchSelectorStage selectorStage = dispatchSelectorService.evaluate(
                request,
                etaStage.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                scenarioStage);
        DecisionStageOutputV1 finalSelectionOutput = runDecisionSidecar(
                resolvedDecisionBrain,
                contextAssembler.finalSelectionInput(request, etaStage.etaContext(), selectorStage));
        DispatchExecutorStage executorStage = dispatchExecutorService.evaluate(
                request,
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                selectorStage);
        DecisionStageOutputV1 executionOutput = runDecisionSidecar(
                resolvedDecisionBrain,
                contextAssembler.safetyExecuteInput(request, etaStage.etaContext(), executorStage));
        long totalDispatchLatencyMs = elapsedMs(dispatchStartedAt);
        List<DispatchStageLatency> stageLatencies = finalizeStageLatencies(
                mergeStageLatencies(
                        etaStage.stageLatencies(),
                        pairClusterStage.stageLatencies(),
                        bundleStage.stageLatencies(),
                        routeCandidateStage.stageLatencies(),
                        routeProposalStage.stageLatencies(),
                        scenarioStage.stageLatencies(),
                        selectorStage.stageLatencies(),
                        executorStage.stageLatencies()),
                reusePlan,
                totalDispatchLatencyMs);
        DispatchLatencyBudgetSummary latencyBudgetSummary = latencyBudgetSummary(stageLatencies, totalDispatchLatencyMs);
        List<String> budgetDegradeReasons = budgetDegradeReasons(stageLatencies, latencyBudgetSummary);
        List<String> degradeReasons = java.util.stream.Stream.concat(
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
                                java.util.stream.Stream.concat(
                                        executorStage.degradeReasons().stream(),
                                        budgetDegradeReasons.stream())))
                .distinct()
                .toList();
        List<MlStageMetadata> mlStageMetadata = java.util.stream.Stream.of(
                        etaStage.mlStageMetadata().stream(),
                        pairClusterStage.mlStageMetadata().stream(),
                        bundleStage.mlStageMetadata().stream(),
                        routeCandidateStage.mlStageMetadata().stream(),
                        routeProposalStage.mlStageMetadata().stream(),
                        scenarioStage.mlStageMetadata().stream())
                .flatMap(stream -> stream)
                .distinct()
                .toList();
        List<LiveStageMetadata> liveStageMetadata = java.util.stream.Stream.of(
                        etaStage.liveStageMetadata().stream())
                .flatMap(stream -> stream)
                .distinct()
                .toList();
        DispatchV2Result result = new DispatchV2Result(
                "dispatch-v2-result/v1",
                request.traceId(),
                false,
                null,
                DECISION_STAGES,
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
                stageLatencies,
                latencyBudgetSummary,
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
                        reusePlan.reuseState() == null ? List.of() : reusePlan.reuseState().clusterSignatures(),
                        reusePlan.reuseState() == null ? List.of() : reusePlan.reuseState().bundleSignatures(),
                        reusePlan.reuseState() == null ? List.of() : reusePlan.reuseState().routeProposals().stream()
                                .map(proposal -> proposal.proposalId()
                                        + "|" + proposal.bundleId()
                                        + "|" + proposal.driverId()
                                        + "|" + String.join(",", proposal.stopOrder()))
                                .sorted()
                                .toList(),
                        List.of(),
                        reusePlan.reuseEligible(),
                        false,
                        false,
                        false,
                        0,
                        0,
                        latencyBudgetSummary.estimatedHotStartSavedMs(),
                        stageLatencies.stream().filter(DispatchStageLatency::hotStartReused).map(DispatchStageLatency::stageName).toList(),
                        reusePlan.degradeReasons()),
                degradeReasons);
        decisionStageLogger.writeFamily("decision_stage_join", request.traceId(), "final-selection", java.util.Map.of(
                "requestedBrain", resolvedDecisionBrain.requestedType().name(),
                "appliedBrain", resolvedDecisionBrain.appliedType().name(),
                "selectedProposalIds", selectorStage.globalSelectionResult().selectedProposals().stream()
                        .map(selectedProposal -> selectedProposal.proposalId())
                        .toList(),
                "brainSelectedIds", finalSelectionOutput == null ? List.of() : finalSelectionOutput.selectedIds()));
        decisionStageLogger.writeFamily("dispatch_execution", request.traceId(), "dispatch-executor", executorStage.dispatchExecutionSummary());
        decisionStageLogger.writeFamily("route_outcome_trace", request.traceId(), "dispatch-executor", java.util.Map.of(
                "assignmentIds", executorStage.assignments().stream().map(assignment -> assignment.assignmentId()).toList(),
                "selectedProposalIds", selectorStage.globalSelectionResult().selectedProposals().stream()
                        .map(selectedProposal -> selectedProposal.proposalId())
                        .toList(),
                "executionBrainSelectedIds", executionOutput == null ? List.of() : executionOutput.selectedIds()));
        decisionStageLogger.writeFamily("dispatch_outcome", request.traceId(), "dispatch-result", java.util.Map.of(
                "traceId", request.traceId(),
                "assignmentCount", executorStage.assignments().size(),
                "degradeReasons", degradeReasons));
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

    private DecisionStageOutputV1 runDecisionSidecar(ResolvedDecisionBrain resolvedDecisionBrain, DecisionStageInputV1 input) {
        decisionStageLogger.writeFamily("decision_stage_input", input.traceId(), input.stageName().wireName(), input);
        DecisionStageOutputV1 output = resolvedDecisionBrain.brain().evaluateStage(input);
        decisionStageLogger.writeFamily("decision_stage_output", input.traceId(), input.stageName().wireName(), output);
        decisionStageLogger.writeFamily("decision_usage", input.traceId(), input.stageName().wireName(), new DecisionUsageRecord(
                "decision-usage/v1",
                input.traceId(),
                input.runId(),
                input.tickId(),
                input.stageName(),
                resolvedDecisionBrain.requestedType(),
                output.brainType(),
                resolvedDecisionBrain.fallbackUsed() || output.meta().fallbackUsed(),
                resolvedDecisionBrain.fallbackUsed() ? resolvedDecisionBrain.fallbackReason() : output.meta().fallbackReason(),
                properties.getDecision().getLlm().getProvider(),
                properties.getDecision().getLlm().getModel()));
        return output;
    }

    private List<DispatchStageLatency> mergeStageLatencies(List<DispatchStageLatency>... stageLatencyLists) {
        Map<String, DispatchStageLatency> byStage = new LinkedHashMap<>();
        for (List<DispatchStageLatency> stageLatencyList : stageLatencyLists) {
            if (stageLatencyList == null) {
                continue;
            }
            for (DispatchStageLatency stageLatency : stageLatencyList) {
                byStage.put(stageLatency.stageName(), stageLatency);
            }
        }
        List<DispatchStageLatency> merged = new ArrayList<>();
        for (String stageName : DECISION_STAGES) {
            DispatchStageLatency stageLatency = byStage.get(stageName);
            if (stageLatency != null) {
                merged.add(stageLatency);
            }
        }
        return List.copyOf(merged);
    }

    private List<DispatchStageLatency> finalizeStageLatencies(List<DispatchStageLatency> rawStageLatencies,
                                                              HotStartReusePlan reusePlan,
                                                              long totalDispatchLatencyMs) {
        if (!properties.getPerformance().isTelemetryEnabled()) {
            return List.of();
        }
        Map<String, DispatchStageLatency> previousByStage = previousStageLatencies(reusePlan.reuseState());
        List<DispatchStageLatency> finalized = new ArrayList<>();
        for (DispatchStageLatency stageLatency : rawStageLatencies) {
            Duration stageBudget = properties.getPerformance().getStageBudgets().get(stageLatency.stageName());
            long budgetMs = stageBudget == null ? 0L : Math.max(0L, stageBudget.toMillis());
            boolean budgetBreached = budgetMs > 0L && stageLatency.elapsedMs() > budgetMs;
            long estimatedSavedMs = 0L;
            if (stageLatency.hotStartReused()) {
                DispatchStageLatency previousStageLatency = previousByStage.get(stageLatency.stageName());
                if (previousStageLatency != null) {
                    estimatedSavedMs = Math.max(0L, previousStageLatency.elapsedMs() - stageLatency.elapsedMs());
                }
            }
            finalized.add(stageLatency
                    .withBudget(budgetMs, budgetBreached)
                    .withEstimatedSavedMs(estimatedSavedMs));
        }
        return List.copyOf(finalized);
    }

    private Map<String, DispatchStageLatency> previousStageLatencies(DispatchRuntimeReuseState reuseState) {
        if (reuseState == null || reuseState.stageLatencies() == null) {
            return Map.of();
        }
        Map<String, DispatchStageLatency> byStage = new LinkedHashMap<>();
        for (DispatchStageLatency stageLatency : reuseState.stageLatencies()) {
            byStage.put(stageLatency.stageName(), stageLatency);
        }
        return byStage;
    }

    private DispatchLatencyBudgetSummary latencyBudgetSummary(List<DispatchStageLatency> stageLatencies,
                                                              long totalDispatchLatencyMs) {
        if (!properties.getPerformance().isTelemetryEnabled()) {
            return DispatchLatencyBudgetSummary.empty();
        }
        long totalBudgetMs = Math.max(0L, properties.getPerformance().getTotalDispatchBudget().toMillis());
        boolean totalBudgetBreached = totalBudgetMs > 0L && totalDispatchLatencyMs > totalBudgetMs;
        List<String> breachedStageNames = stageLatencies.stream()
                .filter(DispatchStageLatency::budgetBreached)
                .map(DispatchStageLatency::stageName)
                .toList();
        long estimatedHotStartSavedMs = stageLatencies.stream()
                .mapToLong(DispatchStageLatency::estimatedSavedMs)
                .sum();
        return new DispatchLatencyBudgetSummary(
                "dispatch-latency-budget-summary/v1",
                totalDispatchLatencyMs,
                totalBudgetMs,
                totalBudgetBreached,
                breachedStageNames,
                estimatedHotStartSavedMs);
    }

    private List<String> budgetDegradeReasons(List<DispatchStageLatency> stageLatencies,
                                              DispatchLatencyBudgetSummary latencyBudgetSummary) {
        if (!properties.getPerformance().isBudgetEnforcementEnabled()) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        for (DispatchStageLatency stageLatency : stageLatencies) {
            if (stageLatency.budgetBreached()) {
                reasons.add("dispatch-stage-budget-breached:" + stageLatency.stageName());
            }
        }
        if (latencyBudgetSummary.totalBudgetBreached()) {
            reasons.add("dispatch-total-budget-breached");
        }
        return List.copyOf(reasons);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}

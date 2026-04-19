package com.routechain.v2.executor;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.harvest.emitters.DispatchHarvestService;
import com.routechain.v2.harvest.writers.NoOpHarvestWriter;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.selector.DispatchSelectorStage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DispatchExecutorService {
    private final DispatchExecutor dispatchExecutor;
    private final DispatchHarvestService dispatchHarvestService;

    public DispatchExecutorService(DispatchExecutor dispatchExecutor) {
        this(
                dispatchExecutor,
                new DispatchHarvestService(com.routechain.config.RouteChainDispatchV2Properties.defaults().getHarvest(), new NoOpHarvestWriter()));
    }

    public DispatchExecutorService(DispatchExecutor dispatchExecutor,
                                  DispatchHarvestService dispatchHarvestService) {
        this.dispatchExecutor = dispatchExecutor;
        this.dispatchHarvestService = dispatchHarvestService;
    }

    public DispatchExecutorStage evaluate(DispatchV2Request request,
                                          DispatchPairClusterStage pairClusterStage,
                                          DispatchBundleStage bundleStage,
                                          DispatchRouteCandidateStage routeCandidateStage,
                                          DispatchRouteProposalStage routeProposalStage,
                                          DispatchSelectorStage selectorStage) {
        long executorStartedAt = System.nanoTime();
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request.availableDrivers(),
                pairClusterStage,
                bundleStage);
        DispatchExecutorResult executionResult = dispatchExecutor.execute(
                selectorStage.globalSelectionResult(),
                selectorStage.selectorCandidates(),
                routeProposalStage.routeProposals(),
                routeCandidateStage,
                context);
        executionResult.trace();
        emitExecution(request, selectorStage, executionResult);
        return new DispatchExecutorStage(
                "dispatch-executor-stage/v2",
                executionResult.assignments(),
                summarize(executionResult, executionResult.degradeReasons()),
                List.of(DispatchStageLatency.measured("dispatch-executor", elapsedMs(executorStartedAt), false)),
                executionResult.degradeReasons());
    }

    private DispatchExecutionSummary summarize(DispatchExecutorResult executionResult,
                                               List<String> degradeReasons) {
        List<DispatchAssignment> assignments = executionResult.assignments();
        int skippedProposalCount = executionResult.selectedProposalCount() - assignments.size();
        return new DispatchExecutionSummary(
                "dispatch-execution-summary/v2",
                executionResult.selectedProposalCount(),
                executionResult.resolvedProposalCount(),
                assignments.size(),
                skippedProposalCount,
                executionResult.resolvedButRejectedCount(),
                degradeReasons);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void emitExecution(DispatchV2Request request,
                               DispatchSelectorStage selectorStage,
                               DispatchExecutorResult executionResult) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("selectedProposalIds", selectorStage.globalSelectionResult().selectedProposals().stream().map(selected -> selected.proposalId()).toList());
        payload.put("selectedAssignmentIds", executionResult.assignments().stream().map(DispatchAssignment::assignmentId).toList());
        payload.put("executedAssignmentCount", executionResult.assignments().size());
        payload.put("executorValidationResult", executionResult.trace());
        payload.put("conflictFreeEvidence", executionResult.degradeReasons().isEmpty());
        payload.put("degradeReasons", executionResult.degradeReasons());
        payload.put("fallbackReasons", List.of());
        dispatchHarvestService.writeRecords("dispatch-execution", "dispatch-executor", request, List.of(new LinkedHashMap<>(payload)));
    }
}

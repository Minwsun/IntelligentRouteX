package com.routechain.v2.executor;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.selector.DispatchSelectorStage;

import java.util.EnumMap;
import java.util.List;

public final class DispatchExecutorService {
    private final DispatchExecutor dispatchExecutor;

    public DispatchExecutorService(DispatchExecutor dispatchExecutor) {
        this.dispatchExecutor = dispatchExecutor;
    }

    public DispatchExecutorStage evaluate(DispatchV2Request request,
                                          DispatchPairClusterStage pairClusterStage,
                                          DispatchBundleStage bundleStage,
                                          DispatchRouteProposalStage routeProposalStage,
                                          DispatchSelectorStage selectorStage) {
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request.availableDrivers(),
                pairClusterStage,
                bundleStage);
        DispatchExecutorResult executionResult = dispatchExecutor.execute(
                selectorStage.globalSelectionResult(),
                selectorStage.selectorCandidates(),
                routeProposalStage.routeProposals(),
                context);
        executionResult.trace();
        return new DispatchExecutorStage(
                "dispatch-executor-stage/v1",
                executionResult.assignments(),
                summarize(selectorStage.globalSelectionResult().selectedCount(), executionResult.assignments(), executionResult.degradeReasons()),
                executionResult.selectedRouteId(),
                executionResult.degradeReasons());
    }

    private DispatchExecutionSummary summarize(int selectedProposalCount,
                                               List<DispatchAssignment> assignments,
                                               List<String> degradeReasons) {
        EnumMap<ExecutionActionType, Integer> actionTypeCounts = new EnumMap<>(ExecutionActionType.class);
        assignments.forEach(assignment -> actionTypeCounts.merge(assignment.actionType(), 1, Integer::sum));
        int executedDriverCount = (int) assignments.stream().map(DispatchAssignment::driverId).distinct().count();
        int executedOrderCount = assignments.stream()
                .flatMap(assignment -> assignment.orderIds().stream())
                .collect(java.util.stream.Collectors.toSet())
                .size();
        return new DispatchExecutionSummary(
                "dispatch-execution-summary/v1",
                selectedProposalCount,
                assignments.size(),
                executedDriverCount,
                executedOrderCount,
                actionTypeCounts,
                degradeReasons);
    }
}

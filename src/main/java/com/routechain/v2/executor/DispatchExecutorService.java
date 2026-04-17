package com.routechain.v2.executor;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.selector.DispatchSelectorStage;

import java.util.List;

public final class DispatchExecutorService {
    private final DispatchExecutor dispatchExecutor;

    public DispatchExecutorService(DispatchExecutor dispatchExecutor) {
        this.dispatchExecutor = dispatchExecutor;
    }

    public DispatchExecutorStage evaluate(DispatchV2Request request,
                                          DispatchPairClusterStage pairClusterStage,
                                          DispatchBundleStage bundleStage,
                                          DispatchRouteCandidateStage routeCandidateStage,
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
                routeCandidateStage,
                context);
        executionResult.trace();
        return new DispatchExecutorStage(
                "dispatch-executor-stage/v2",
                executionResult.assignments(),
                summarize(executionResult, executionResult.degradeReasons()),
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
}

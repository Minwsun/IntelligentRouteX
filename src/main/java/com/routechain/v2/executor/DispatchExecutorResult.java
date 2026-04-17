package com.routechain.v2.executor;

import java.util.List;

record DispatchExecutorResult(
        List<DispatchAssignment> assignments,
        int selectedProposalCount,
        int resolvedProposalCount,
        int resolvedButRejectedCount,
        DispatchExecutionTrace trace,
        List<String> degradeReasons) {
}

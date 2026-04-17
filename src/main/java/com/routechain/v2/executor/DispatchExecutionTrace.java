package com.routechain.v2.executor;

import java.util.List;

record DispatchExecutionTrace(
        List<String> missingContextProposalIds,
        List<String> executorOrdering,
        List<String> assignmentBuildReasons,
        List<String> conflictRejectedProposalIds,
        List<String> emittedAssignmentIds,
        String summaryReason) {

    static DispatchExecutionTrace empty() {
        return new DispatchExecutionTrace(List.of(), List.of(), List.of(), List.of(), List.of(), "no-selection");
    }
}

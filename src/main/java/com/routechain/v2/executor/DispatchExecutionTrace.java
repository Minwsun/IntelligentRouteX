package com.routechain.v2.executor;

import java.util.List;

record DispatchExecutionTrace(
        List<String> missingContextProposalIds,
        List<String> executorOrdering,
        List<String> assignmentBuildReasons,
        String selectedRouteIdReason) {

    static DispatchExecutionTrace empty() {
        return new DispatchExecutionTrace(List.of(), List.of(), List.of(), "no-selection");
    }
}

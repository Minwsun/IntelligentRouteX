package com.routechain.v2.executor;

import java.util.Optional;

record DispatchAssignmentBuildResult(
        Optional<DispatchAssignment> assignment,
        DispatchExecutionTrace trace,
        java.util.List<String> degradeReasons) {
}

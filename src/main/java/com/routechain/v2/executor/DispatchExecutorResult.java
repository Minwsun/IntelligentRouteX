package com.routechain.v2.executor;

import java.util.List;

record DispatchExecutorResult(
        List<DispatchAssignment> assignments,
        String selectedRouteId,
        DispatchExecutionTrace trace,
        List<String> degradeReasons) {
}

package com.routechain.v2.executor;

import com.routechain.v2.SchemaVersioned;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DispatchExecutionSummary(
        String schemaVersion,
        int selectedProposalCount,
        int assignmentCount,
        int executedDriverCount,
        int executedOrderCount,
        Map<ExecutionActionType, Integer> actionTypeCounts,
        List<String> degradeReasons) implements SchemaVersioned {

    public static DispatchExecutionSummary empty() {
        return new DispatchExecutionSummary(
                "dispatch-execution-summary/v1",
                0,
                0,
                0,
                0,
                new EnumMap<>(ExecutionActionType.class),
                List.of());
    }
}

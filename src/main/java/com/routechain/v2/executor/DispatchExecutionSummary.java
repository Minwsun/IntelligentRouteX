package com.routechain.v2.executor;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchExecutionSummary(
        String schemaVersion,
        int selectedProposalCount,
        int resolvedProposalCount,
        int executedAssignmentCount,
        int skippedProposalCount,
        int resolvedButRejectedCount,
        List<String> degradeReasons) implements SchemaVersioned {

    public static DispatchExecutionSummary empty() {
        return new DispatchExecutionSummary(
                "dispatch-execution-summary/v2",
                0,
                0,
                0,
                0,
                0,
                List.of());
    }
}

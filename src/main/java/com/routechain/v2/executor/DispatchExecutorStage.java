package com.routechain.v2.executor;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchExecutorStage(
        String schemaVersion,
        List<DispatchAssignment> assignments,
        DispatchExecutionSummary dispatchExecutionSummary,
        List<String> degradeReasons) implements SchemaVersioned {
}

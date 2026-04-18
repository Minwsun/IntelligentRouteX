package com.routechain.v2.executor;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.DispatchStageLatency;

import java.util.List;

public record DispatchExecutorStage(
        String schemaVersion,
        List<DispatchAssignment> assignments,
        DispatchExecutionSummary dispatchExecutionSummary,
        List<DispatchStageLatency> stageLatencies,
        List<String> degradeReasons) implements SchemaVersioned {
}

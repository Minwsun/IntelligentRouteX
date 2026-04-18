package com.routechain.v2.perf;

import com.routechain.v2.SchemaVersioned;

public record DispatchPerfStageLatencyStats(
        String schemaVersion,
        String stageName,
        DispatchPerfNumericStats latencyStats) implements SchemaVersioned {
}

package com.routechain.v2.perf;

import com.routechain.v2.SchemaVersioned;

import java.time.Instant;
import java.util.List;

public record DispatchPerfBenchmarkSummary(
        String schemaVersion,
        Instant generatedAt,
        int resultCount,
        List<DispatchPerfBenchmarkResult> results,
        List<String> notes) implements SchemaVersioned {
}

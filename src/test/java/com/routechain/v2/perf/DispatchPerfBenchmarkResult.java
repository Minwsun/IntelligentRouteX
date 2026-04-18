package com.routechain.v2.perf;

import com.routechain.v2.SchemaVersioned;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DispatchPerfBenchmarkResult(
        String schemaVersion,
        Instant benchmarkTimestamp,
        String gitCommit,
        DispatchPerfMachineProfile machineProfile,
        String baselineId,
        String workloadSize,
        String runMode,
        String selectorMode,
        int orderCount,
        int driverCount,
        long workloadSeed,
        int warmupRuns,
        int measuredRuns,
        int totalDispatchCount,
        boolean deferred,
        DispatchPerfNumericStats totalLatencyStats,
        List<DispatchPerfStageLatencyStats> stageLatencyStats,
        double budgetBreachRate,
        Map<String, Integer> reusedStageNames,
        DispatchPerfNumericStats estimatedSavedMsStats,
        Map<String, Integer> workerCallCounts,
        Map<String, Integer> liveSourceCallCounts,
        Long memoryUsedMb,
        List<String> notes) implements SchemaVersioned {
}

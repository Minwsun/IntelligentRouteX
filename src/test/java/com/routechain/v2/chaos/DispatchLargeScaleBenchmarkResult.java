package com.routechain.v2.chaos;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.benchmark.DispatchQualityMetrics;
import com.routechain.v2.perf.DispatchPerfMachineProfile;
import com.routechain.v2.perf.DispatchPerfNumericStats;
import com.routechain.v2.perf.DispatchPerfStageLatencyStats;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DispatchLargeScaleBenchmarkResult(
        String schemaVersion,
        Instant benchmarkTimestamp,
        String gitCommit,
        DispatchPerfMachineProfile machineProfile,
        String executionMode,
        String baselineId,
        String scenarioPack,
        String scenarioName,
        String workloadSize,
        int runCount,
        boolean deferred,
        DispatchPerfNumericStats totalLatencyStats,
        List<DispatchPerfStageLatencyStats> stageLatencyStats,
        DispatchQualityMetrics qualitySummary,
        double budgetBreachRate,
        Map<String, Integer> reusedStageFrequency,
        DispatchPerfNumericStats estimatedSavedMsStats,
        double workerFallbackRate,
        double liveSourceFallbackRate,
        Map<String, Integer> degradeReasonCounts,
        boolean passed,
        List<String> notes) implements SchemaVersioned {
}

package com.routechain.v2.chaos;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.perf.DispatchPerfMachineProfile;

import java.time.Instant;
import java.util.List;

public record DispatchSoakRunResult(
        String schemaVersion,
        Instant benchmarkTimestamp,
        String gitCommit,
        DispatchPerfMachineProfile machineProfile,
        String executionMode,
        String runAuthorityClass,
        boolean authoritative,
        boolean authorityEligible,
        boolean sampleCountOverrideApplied,
        String durationProfile,
        String scenarioPack,
        String workloadSize,
        int sampleCount,
        DispatchSoakNumericTrend latencyTrend,
        DispatchSoakNumericTrend budgetBreachTrend,
        DispatchSoakNumericTrend memoryUsageTrend,
        DispatchSoakNumericTrend workerFallbackTrend,
        DispatchSoakNumericTrend liveSourceFallbackTrend,
        DispatchSoakNumericTrend reuseHitTrend,
        String snapshotStability,
        boolean replayIsolationMaintained,
        List<String> failureSummaries,
        boolean passed,
        List<String> notes) implements SchemaVersioned {
}

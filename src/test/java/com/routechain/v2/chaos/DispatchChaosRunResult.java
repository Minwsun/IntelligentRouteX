package com.routechain.v2.chaos;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.perf.DispatchPerfMachineProfile;

import java.time.Instant;
import java.util.List;

public record DispatchChaosRunResult(
        String schemaVersion,
        Instant benchmarkTimestamp,
        String gitCommit,
        DispatchPerfMachineProfile machineProfile,
        String executionMode,
        String scenarioPack,
        String scenarioName,
        String faultType,
        String workloadSize,
        int dispatchCount,
        boolean deferred,
        List<String> degradeReasons,
        List<String> correctnessMismatchReasons,
        boolean conflictFreeAssignments,
        List<String> budgetBreachedStageNames,
        boolean totalBudgetBreached,
        List<String> reusedStageNames,
        boolean passed,
        List<String> notes) implements SchemaVersioned {
}

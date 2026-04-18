package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

public record DispatchQualityMetrics(
        String schemaVersion,
        int selectedProposalCount,
        int executedAssignmentCount,
        boolean conflictFreeAssignments,
        double bundleRate,
        double averageBundleSize,
        double routeFallbackRate,
        double averageProjectedPickupEtaMinutes,
        double averageProjectedCompletionEtaMinutes,
        double landingValueAverage,
        double robustUtilityAverage,
        double selectorObjectiveValue,
        double degradeRate,
        double workerFallbackRate,
        double liveSourceFallbackRate) implements SchemaVersioned {
}

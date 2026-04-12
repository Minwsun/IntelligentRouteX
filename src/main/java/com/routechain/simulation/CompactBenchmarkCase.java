package com.routechain.simulation;

public record CompactBenchmarkCase(
        long seed,
        String regime,
        RunReport baseline,
        RunReport compact,
        RunReport omegaReference,
        String compactSnapshotTag,
        boolean compactRollbackAvailable,
        java.util.List<String> compactTopExplanations,
        java.util.Map<String, Integer> compactPlanTypeCounts,
        java.util.Map<String, Integer> routeSourceCounts,
        int batchEligibleContexts,
        int batchChosenWhenEligibleContexts,
        int singleChosenWhenBatchEligibleContexts,
        java.util.Map<String, Integer> batchRejectionReasons,
        CalibrationSnapshot calibrationSnapshot,
        double bundleSuccessRate,
        double avgObservedBundleSize,
        double bundleThreePlusRate) {
}

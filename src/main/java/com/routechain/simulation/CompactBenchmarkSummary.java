package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

public record CompactBenchmarkSummary(
        Instant generatedAt,
        String lane,
        String scope,
        String baselinePolicy,
        List<String> regimes,
        List<CompactBenchmarkCase> cases,
        double compactCompletionDeltaVsBaseline,
        double compactOnTimeDeltaVsBaseline,
        double compactDeadheadDeltaVsBaseline,
        double compactPostDropHitDeltaVsBaseline,
        double compactEmptyKmDeltaVsBaseline,
        double compactDeadheadImprovementPctVsBaseline,
        double compactEmptyKmImprovementPctVsBaseline,
        java.util.Map<String, Integer> compactPlanTypeCounts,
        java.util.Map<String, Integer> routeSourceCounts,
        double compactBundleSuccessRate,
        double compactAvgObservedBundleSize,
        double compactBundleThreePlusRate,
        boolean noSevereSeedRegression,
        String latestSnapshotPath,
        double compactCompletionDeltaVsOmega,
        double compactDeadheadDeltaVsOmega) {
}

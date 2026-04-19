package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchQualityComparisonReport(
        String schemaVersion,
        String scenarioPack,
        String scenarioName,
        String workloadSize,
        String executionMode,
        String runAuthorityClass,
        boolean authoritative,
        boolean authorityEligible,
        boolean sampleCountOverrideApplied,
        List<DispatchQualityBenchmarkResult> baselineResults,
        List<String> fullV2Advantages,
        List<String> fullV2Regressions,
        String comparisonSummary) implements SchemaVersioned {
}

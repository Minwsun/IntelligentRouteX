package com.routechain.simulation;

import java.util.List;

/**
 * Structured ablation result for policy comparisons.
 */
public record PolicyAblationResult(
        String schemaVersion,
        String ablationId,
        String scenarioName,
        String baselinePolicy,
        String candidatePolicy,
        String verdict,
        double overallGainPercent,
        BenchmarkStatSummary gainSummary,
        BenchmarkStatSummary completionDeltaSummary,
        BenchmarkStatSummary deadheadDeltaSummary,
        List<BenchmarkStatSummary> additionalSummaries
) {
    public PolicyAblationResult {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION : schemaVersion;
        additionalSummaries = additionalSummaries == null
                ? List.of() : List.copyOf(additionalSummaries);
    }
}


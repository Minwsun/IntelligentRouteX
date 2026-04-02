package com.routechain.simulation;

/**
 * Drift snapshot for policy/model monitoring across benchmark windows.
 */
public record DispatchDriftSnapshot(
        String schemaVersion,
        String scope,
        String metricName,
        double baselineMean,
        double candidateMean,
        double drift,
        double threshold,
        boolean drifted
) {
    public DispatchDriftSnapshot {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION : schemaVersion;
    }
}

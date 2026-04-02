package com.routechain.simulation;

/**
 * Canonical statistical summary for benchmark metrics.
 */
public record BenchmarkStatSummary(
        String schemaVersion,
        String metricName,
        String metricClass,
        String scope,
        int sampleCount,
        double mean,
        double median,
        double p95,
        double stdDev,
        double ci95Low,
        double ci95High,
        Double effectSizeCohensD,
        Double pValue,
        Boolean significantAt95
) {
    public BenchmarkStatSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION : schemaVersion;
        metricClass = metricClass == null || metricClass.isBlank()
                ? "business"
                : metricClass;
    }
}

package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Summary of whether selected batch-2 plans beat local comparators.
 */
public record BatchIntelligenceCertificationSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        int batch2SampleCount,
        int contextsWithSingleComparator,
        int contextsWithExtensionComparator,
        double utilityVsSingleMean,
        double utilityVsExtensionMean,
        double marginalDeadheadPerAddedOrderMean,
        double lastDropLandingScoreMean,
        double postDropDemandProbabilityMean,
        double expectedPostCompletionEmptyKmMean,
        double inflationRate,
        boolean overallPass,
        List<String> notes
) {
    public BatchIntelligenceCertificationSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION
                : schemaVersion;
        laneName = laneName == null || laneName.isBlank() ? "certification" : laneName;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

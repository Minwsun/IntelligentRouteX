package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Phase-3 evidence pack that explains why route-quality buckets are passing or failing.
 */
public record RouteQualityBlockerSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        List<RouteQualityBlockerBucketSummary> bucketSummaries,
        List<String> notes
) {
    public RouteQualityBlockerSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION
                : schemaVersion;
        laneName = laneName == null || laneName.isBlank() ? "route-quality-blockers" : laneName;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        bucketSummaries = bucketSummaries == null ? List.of() : List.copyOf(bucketSummaries);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Certification summary for public community research benchmark evidence.
 */
public record PublicResearchBenchmarkSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        List<ResearchBenchmarkFamilyResult> familyResults,
        boolean overallPass,
        List<String> notes
) {
    public PublicResearchBenchmarkSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION
                : schemaVersion;
        laneName = laneName == null || laneName.isBlank() ? "certification" : laneName;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        familyResults = familyResults == null ? List.of() : List.copyOf(familyResults);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

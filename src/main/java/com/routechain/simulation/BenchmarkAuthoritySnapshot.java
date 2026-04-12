package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Captures whether a benchmark lane was summarized from a clean or dirty tracked workspace.
 */
public record BenchmarkAuthoritySnapshot(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        boolean workspaceDirty,
        boolean authorityDirty,
        boolean authorityDetectionFailed,
        List<String> dirtyTrackedPaths,
        List<String> dirtyAuthorityPaths,
        List<String> notes
) {
    public BenchmarkAuthoritySnapshot {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        laneName = laneName == null || laneName.isBlank() ? "smoke" : laneName;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        dirtyTrackedPaths = dirtyTrackedPaths == null ? List.of() : List.copyOf(dirtyTrackedPaths);
        dirtyAuthorityPaths = dirtyAuthorityPaths == null ? List.of() : List.copyOf(dirtyAuthorityPaths);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean triageOnly() {
        return authorityDirty || authorityDetectionFailed;
    }

    public boolean cleanCheckpointEligible() {
        return !authorityDirty && !authorityDetectionFailed;
    }
}

package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Declares whether a benchmark checkpoint is clean enough for canonical reading
 * or only usable for triage.
 */
public record BenchmarkCheckpointSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        String checkpointStatus,
        boolean cleanCheckpoint,
        boolean triageOnly,
        boolean routeAiSummaryPresent,
        boolean repoSummaryPresent,
        boolean verdictSummaryPresent,
        boolean blockerSummaryPresent,
        String routeAiVerdict,
        String repoVerdict,
        String routingVerdict,
        List<String> notes
) {
    public BenchmarkCheckpointSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        laneName = laneName == null || laneName.isBlank() ? "smoke" : laneName;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        checkpointStatus = checkpointStatus == null || checkpointStatus.isBlank()
                ? "DIRTY_TRIAGE_ONLY"
                : checkpointStatus;
        routeAiVerdict = routeAiVerdict == null || routeAiVerdict.isBlank() ? "MISSING" : routeAiVerdict;
        repoVerdict = repoVerdict == null || repoVerdict.isBlank() ? "MISSING" : repoVerdict;
        routingVerdict = routingVerdict == null || routingVerdict.isBlank() ? "MISSING" : routingVerdict;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

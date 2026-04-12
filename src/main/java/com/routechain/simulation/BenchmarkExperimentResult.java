package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Captures the materialized outcome of one triage or canonical experiment run.
 */
public record BenchmarkExperimentResult(
        String schemaVersion,
        String experimentId,
        Instant generatedAt,
        String hypothesisId,
        String laneType,
        String baselineCheckpointId,
        String candidateCheckpointId,
        String gitRevision,
        String authorityStatus,
        String checkpointStatus,
        String seedSetType,
        String artifactRoot,
        String routeAiVerdict,
        String repoVerdict,
        String routingVerdict,
        boolean blockerSummaryPresent,
        boolean promising,
        List<String> notes
) {
    public BenchmarkExperimentResult {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        experimentId = experimentId == null || experimentId.isBlank()
                ? "experiment-" + Instant.now().toEpochMilli()
                : experimentId;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        hypothesisId = hypothesisId == null || hypothesisId.isBlank() ? "unspecified-hypothesis" : hypothesisId;
        laneType = laneType == null || laneType.isBlank() ? "triage" : laneType;
        baselineCheckpointId = baselineCheckpointId == null || baselineCheckpointId.isBlank()
                ? "unknown-checkpoint"
                : baselineCheckpointId;
        candidateCheckpointId = candidateCheckpointId == null || candidateCheckpointId.isBlank()
                ? "unknown-checkpoint"
                : candidateCheckpointId;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        authorityStatus = authorityStatus == null || authorityStatus.isBlank() ? "UNKNOWN" : authorityStatus;
        checkpointStatus = checkpointStatus == null || checkpointStatus.isBlank()
                ? "UNKNOWN"
                : checkpointStatus;
        seedSetType = seedSetType == null || seedSetType.isBlank() ? "tuning" : seedSetType;
        artifactRoot = artifactRoot == null || artifactRoot.isBlank() ? "unknown" : artifactRoot;
        routeAiVerdict = routeAiVerdict == null || routeAiVerdict.isBlank() ? "MISSING" : routeAiVerdict;
        repoVerdict = repoVerdict == null || repoVerdict.isBlank() ? "MISSING" : repoVerdict;
        routingVerdict = routingVerdict == null || routingVerdict.isBlank() ? "MISSING" : routingVerdict;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Declares one benchmark-governed experiment before it runs.
 */
public record BenchmarkExperimentSpec(
        String schemaVersion,
        String experimentId,
        Instant createdAt,
        String hypothesisId,
        String laneType,
        String baselineCheckpointId,
        String baselineLaneName,
        String gitRevision,
        String authorityStatus,
        String changedKnobGroup,
        List<String> targetBuckets,
        String seedSetType,
        String workspaceStrategy,
        String artifactRoot,
        List<String> notes
) {
    public BenchmarkExperimentSpec {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        experimentId = experimentId == null || experimentId.isBlank()
                ? "experiment-" + Instant.now().toEpochMilli()
                : experimentId;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        hypothesisId = hypothesisId == null || hypothesisId.isBlank() ? "unspecified-hypothesis" : hypothesisId;
        laneType = laneType == null || laneType.isBlank() ? "triage" : laneType;
        baselineCheckpointId = baselineCheckpointId == null || baselineCheckpointId.isBlank()
                ? "unknown-checkpoint"
                : baselineCheckpointId;
        baselineLaneName = baselineLaneName == null || baselineLaneName.isBlank() ? "unknown" : baselineLaneName;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        authorityStatus = authorityStatus == null || authorityStatus.isBlank() ? "UNKNOWN" : authorityStatus;
        changedKnobGroup = changedKnobGroup == null || changedKnobGroup.isBlank()
                ? "unclassified"
                : changedKnobGroup;
        targetBuckets = targetBuckets == null ? List.of() : List.copyOf(targetBuckets);
        seedSetType = seedSetType == null || seedSetType.isBlank() ? "tuning" : seedSetType;
        workspaceStrategy = workspaceStrategy == null || workspaceStrategy.isBlank()
                ? "detached-worktree"
                : workspaceStrategy;
        artifactRoot = artifactRoot == null || artifactRoot.isBlank() ? "unknown" : artifactRoot;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Append-only baseline registry entry for clean canonical checkpoints.
 */
public record BenchmarkBaselineRef(
        String schemaVersion,
        String baselineId,
        String checkpointId,
        String laneName,
        Instant promotedAt,
        String gitRevision,
        String checkpointStatus,
        String promotionDecisionId,
        String supersedesBaselineId,
        boolean active,
        List<String> notes
) {
    public BenchmarkBaselineRef {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        baselineId = baselineId == null || baselineId.isBlank()
                ? "baseline-" + Instant.now().toEpochMilli()
                : baselineId;
        checkpointId = checkpointId == null || checkpointId.isBlank() ? "unknown-checkpoint" : checkpointId;
        laneName = laneName == null || laneName.isBlank() ? "unknown" : laneName;
        promotedAt = promotedAt == null ? Instant.now() : promotedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        checkpointStatus = checkpointStatus == null || checkpointStatus.isBlank()
                ? "UNKNOWN"
                : checkpointStatus;
        promotionDecisionId = promotionDecisionId == null || promotionDecisionId.isBlank()
                ? "untracked-decision"
                : promotionDecisionId;
        supersedesBaselineId = supersedesBaselineId == null ? "" : supersedesBaselineId;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

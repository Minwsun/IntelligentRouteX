package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Records the explicit promotion or rejection decision around a checkpoint or experiment.
 */
public record BenchmarkPromotionDecision(
        String schemaVersion,
        String decisionId,
        Instant decidedAt,
        String decisionType,
        String experimentId,
        String baselineCheckpointId,
        String candidateCheckpointId,
        String laneName,
        String decision,
        boolean canonicalRecheckRequired,
        boolean holdoutRequired,
        List<String> notes
) {
    public BenchmarkPromotionDecision {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        decisionId = decisionId == null || decisionId.isBlank()
                ? "decision-" + Instant.now().toEpochMilli()
                : decisionId;
        decidedAt = decidedAt == null ? Instant.now() : decidedAt;
        decisionType = decisionType == null || decisionType.isBlank() ? "checkpoint" : decisionType;
        experimentId = experimentId == null ? "" : experimentId;
        baselineCheckpointId = baselineCheckpointId == null || baselineCheckpointId.isBlank()
                ? "unknown-checkpoint"
                : baselineCheckpointId;
        candidateCheckpointId = candidateCheckpointId == null || candidateCheckpointId.isBlank()
                ? "unknown-checkpoint"
                : candidateCheckpointId;
        laneName = laneName == null || laneName.isBlank() ? "unknown" : laneName;
        decision = decision == null || decision.isBlank() ? "REJECTED" : decision;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

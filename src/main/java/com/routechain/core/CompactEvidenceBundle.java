package com.routechain.core;

import java.time.Instant;
import java.util.List;

public record CompactEvidenceBundle(
        String runId,
        String modeName,
        Instant decisionTime,
        List<String> selectedPlanIds,
        List<CompactDecisionExplanation> explanations,
        WeightSnapshot weightSnapshotBefore,
        WeightSnapshot weightSnapshotAfter,
        CompactDecisionResolution latestResolution) {

    public static CompactEvidenceBundle empty() {
        return new CompactEvidenceBundle(
                "run-unset",
                "COMPACT",
                Instant.EPOCH,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    public CompactEvidenceBundle withResolution(WeightSnapshot snapshotAfter,
                                                CompactDecisionResolution resolution) {
        return new CompactEvidenceBundle(
                runId,
                modeName,
                decisionTime,
                selectedPlanIds,
                explanations,
                weightSnapshotBefore,
                snapshotAfter,
                resolution);
    }
}

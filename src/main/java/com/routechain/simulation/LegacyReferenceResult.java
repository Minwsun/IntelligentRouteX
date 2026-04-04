package com.routechain.simulation;

import java.util.List;

/**
 * Non-blocking reference status for Legacy vs Omega comparisons.
 */
public record LegacyReferenceResult(
        boolean warning,
        int consecutiveUnderperformCount,
        double latestOverallGainPercent,
        double latestCompletionDelta,
        double latestDeadheadDelta,
        List<String> notes
) {
    public LegacyReferenceResult {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

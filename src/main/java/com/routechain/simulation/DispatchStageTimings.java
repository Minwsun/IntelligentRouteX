package com.routechain.simulation;

/**
 * Per-dispatch timing sample for hot-path stages.
 */
public record DispatchStageTimings(
        long graphShadowProjectionMs,
        long candidateGenerationMs,
        long graphAffinityScoringMs,
        long optimizerSolveMs,
        long fallbackInjectionMs,
        long repositionSelectionMs,
        boolean graphShadowCacheHit,
        int generatedCandidateCount,
        int fullyScoredCandidateCount,
        int availableDriverCount
) {
    public static DispatchStageTimings empty() {
        return new DispatchStageTimings(0L, 0L, 0L, 0L, 0L, 0L, false, 0, 0, 0);
    }
}

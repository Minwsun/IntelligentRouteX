package com.routechain.ai;

import java.util.List;

/**
 * Structured context sent to the LLM shadow/advisory plane.
 */
public record LLMAdvisorRequest(
        String runId,
        String traceId,
        String driverId,
        String executionProfile,
        String activePolicy,
        String stressRegime,
        double[] contextFeatures,
        List<CandidatePlanSummary> candidatePlans,
        LLMRequestClass requestClass,
        int estimatedInputTokens,
        boolean operatorInitiated
) {
    public LLMAdvisorRequest(
            String runId,
            String traceId,
            String driverId,
            String executionProfile,
            String activePolicy,
            String stressRegime,
            double[] contextFeatures,
            List<CandidatePlanSummary> candidatePlans
    ) {
        this(runId, traceId, driverId, executionProfile, activePolicy, stressRegime,
                contextFeatures, candidatePlans, LLMRequestClass.SHADOW_FAST, -1, false);
    }

    public record CandidatePlanSummary(
            String traceId,
            int bundleSize,
            double totalScore,
            double onTimeProbability,
            double deliveryCorridorScore,
            double lastDropLandingScore,
            double expectedPostCompletionEmptyKm,
            boolean selected,
            String explanation
    ) {}
}

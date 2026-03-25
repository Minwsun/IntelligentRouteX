package com.routechain.ai;

import java.util.List;

/**
 * Structured output returned by the LLM shadow/advisory plane.
 */
public record LLMAdvisorResponse(
        String mode,
        boolean triggered,
        String routeIntent,
        String corridorPreference,
        String pickupWaveComment,
        String dropSequenceCritique,
        String softLandingComment,
        List<String> riskFlags,
        double confidence,
        String reasoning,
        String provider,
        String modelId,
        LLMRequestClass requestClass,
        int estimatedInputTokens,
        boolean fallbackApplied,
        String fallbackReason,
        String fallbackChain,
        String quotaDecision,
        long latencyMs
) {
    public LLMAdvisorResponse(
            String mode,
            boolean triggered,
            String routeIntent,
            String corridorPreference,
            String pickupWaveComment,
            String dropSequenceCritique,
            String softLandingComment,
            List<String> riskFlags,
            double confidence,
            String reasoning
    ) {
        this(mode, triggered, routeIntent, corridorPreference, pickupWaveComment,
                dropSequenceCritique, softLandingComment, riskFlags, confidence, reasoning,
                "", "", LLMRequestClass.SHADOW_FAST, -1, false, "", "", "", 0L);
    }

    public static LLMAdvisorResponse skipped(String reason) {
        return new LLMAdvisorResponse(
                "OFF", false, "no-op", "unchanged", "",
                "", "", List.of(), 0.0, reason,
                "offline", "offline-shadow", LLMRequestClass.SHADOW_FAST,
                -1, false, reason, "", "skipped", 0L);
    }
}

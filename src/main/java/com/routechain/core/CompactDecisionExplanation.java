package com.routechain.core;

public record CompactDecisionExplanation(
        String bundleId,
        String driverId,
        CompactPlanType planType,
        String summary,
        AdaptiveScoreBreakdown breakdown) {
}

package com.routechain.core;

public record CompactDecisionExplanation(
        String bundleId,
        String driverId,
        String summary,
        AdaptiveScoreBreakdown breakdown) {
}

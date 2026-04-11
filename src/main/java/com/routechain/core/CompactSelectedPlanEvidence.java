package com.routechain.core;

import java.util.List;

public record CompactSelectedPlanEvidence(
        String traceId,
        String bundleId,
        String driverId,
        CompactPlanType planType,
        List<String> orderIds,
        PlanFeatureVector featureVector,
        AdaptiveScoreBreakdown scoreBreakdown,
        String explanationSummary) {
}

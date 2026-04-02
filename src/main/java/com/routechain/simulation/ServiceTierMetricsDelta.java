package com.routechain.simulation;

/**
 * Tier-level delta view between baseline and candidate runs.
 */
public record ServiceTierMetricsDelta(
        String serviceTier,
        int orderCountDelta,
        int completedOrderCountDelta,
        double completionRateDelta,
        double avgPromisedEtaMinutesDelta,
        double avgQuotedFeeDelta
) {}

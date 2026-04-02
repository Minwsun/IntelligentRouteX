package com.routechain.simulation;

/**
 * Aggregated business view for one delivery service tier in a run.
 */
public record ServiceTierMetrics(
        String serviceTier,
        int orderCount,
        int completedOrderCount,
        double completionRate,
        double avgPromisedEtaMinutes,
        double avgQuotedFee
) {}

package com.routechain.simulation;

/**
 * Compact policy metrics for the JavaFX demo validation artifact.
 */
public record JavaFxPolicyComparison(
        String policyKey,
        String policyLabel,
        String runId,
        String scenarioName,
        double completionRate,
        double deadheadPerCompletedOrderKm,
        double cancellationRate,
        double postDropOrderHitRate,
        double nextOrderIdleMinutes,
        double lastDropGoodZoneRate,
        double bundleRate,
        double oracleObjective
) { }

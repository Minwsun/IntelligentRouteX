package com.routechain.ai;

import java.time.Instant;

/**
 * Versioned dispatch decision context contract for production-small data plane.
 */
public record DecisionContextV2(
        String runId,
        String scenario,
        String serviceTier,
        String cellId,
        Instant featureTimestamp,
        int trafficHorizonMinutes,
        int weatherHorizonMinutes,
        double merchantPrepForecastMinutes,
        double continuationOpportunityScore,
        double postDropDemandProbability,
        double emptyZoneRisk,
        String neuralPriorVersion,
        long neuralPriorFreshnessMs
) {}

package com.routechain.ai;

import java.time.Instant;

/**
 * Versioned dispatch decision context contract for production-small data plane.
 */
public record DecisionContextV2(
        String runId,
        String scenario,
        String cellId,
        Instant featureTimestamp,
        int trafficHorizonMinutes,
        int weatherHorizonMinutes
) {}

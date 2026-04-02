package com.routechain.ai;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical online feature snapshot emitted for each scored/selected dispatch decision.
 */
public record FeatureSnapshotV2(
        String runId,
        String traceId,
        String driverId,
        String scenario,
        String serviceTier,
        String cellId,
        Instant featureTimestamp,
        int trafficHorizonMinutes,
        int weatherHorizonMinutes,
        long forecastFreshnessMs,
        double[] contextFeatures,
        Map<String, Object> contextSnapshot
) {}

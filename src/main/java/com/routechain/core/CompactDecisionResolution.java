package com.routechain.core;

import java.time.Instant;
import java.util.List;

public record CompactDecisionResolution(
        String traceId,
        String driverId,
        String bundleId,
        List<String> orderIds,
        RegimeKey regimeKey,
        PlanFeatureVector featureVector,
        OutcomeVector outcomeVector,
        WeightSnapshot weightSnapshotBefore,
        WeightSnapshot weightSnapshotAfter,
        AdaptiveScoreBreakdown scoreBreakdown,
        boolean postDropHit,
        Instant resolvedAt) {

    public CompactDecisionResolution withSnapshotAfter(WeightSnapshot snapshotAfter, Instant resolvedAt) {
        return new CompactDecisionResolution(
                traceId,
                driverId,
                bundleId,
                orderIds,
                regimeKey,
                featureVector,
                outcomeVector,
                weightSnapshotBefore,
                snapshotAfter,
                scoreBreakdown,
                postDropHit,
                resolvedAt);
    }
}

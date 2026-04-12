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
        DecisionLogRecord decisionLog,
        ResolvedDecisionSample resolvedSample,
        boolean postDropHit,
        Instant resolvedAt) {

    public DecisionOutcomeStage outcomeStage() {
        return resolvedSample == null ? null : resolvedSample.outcomeStage();
    }

    public boolean isFinalResolution() {
        return resolvedSample != null && resolvedSample.eligibleForWeightUpdate();
    }

    public CompactDecisionResolution withSnapshotAfter(WeightSnapshot snapshotAfter, Instant resolvedAt) {
        ResolvedDecisionSample finalizedSample = resolvedSample == null
                ? null
                : new ResolvedDecisionSample(
                resolvedSample.decisionLog(),
                resolvedSample.outcomeVector(),
                resolvedSample.outcomeStage(),
                resolvedSample.actualEtaMinutes(),
                resolvedSample.actualCancelled(),
                resolvedSample.actualPostDropHit(),
                resolvedSample.actualPostCompletionEmptyKm(),
                resolvedSample.actualNextOrderIdleMinutes(),
                resolvedAt);
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
                decisionLog,
                finalizedSample,
                postDropHit,
                resolvedAt);
    }
}

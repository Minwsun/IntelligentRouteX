package com.routechain.core;

import java.time.Instant;
import java.util.List;

public record DecisionLogRecord(
        String decisionId,
        String driverId,
        String bundleId,
        CompactPlanType planType,
        List<String> orderIds,
        RegimeKey regimeKey,
        PlanFeatureVector featureVector,
        AdaptiveScoreBreakdown scoreBreakdown,
        WeightSnapshot snapshotBefore,
        Instant decisionTime,
        double predictedUtilityRaw,
        double predictedRewardNormalized,
        double predictedEtaMinutes,
        double predictedDeadheadKm,
        double predictedRevenue,
        double predictedLandingScore,
        double predictedPostDropDemandProbability,
        double predictedPostCompletionEmptyKm,
        double predictedNextOrderIdleMinutes,
        double predictedCancelRisk,
        double predictedOnTimeProbability,
        double predictedTripDistanceKm) {
}

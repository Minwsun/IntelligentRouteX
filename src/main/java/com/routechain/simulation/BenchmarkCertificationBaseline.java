package com.routechain.simulation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Absolute KPI thresholds used by route intelligence certification lanes.
 */
public record BenchmarkCertificationBaseline(
        String schemaVersion,
        Map<String, ScenarioGroupThresholds> scenarioGroups
) {
    public BenchmarkCertificationBaseline {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "v1"
                : schemaVersion;
        scenarioGroups = scenarioGroups == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(scenarioGroups));
    }

    public ScenarioGroupThresholds thresholdsFor(String scenarioGroup) {
        if (scenarioGroup == null || scenarioGroup.isBlank()) {
            throw new IllegalArgumentException("Scenario group is required");
        }
        ScenarioGroupThresholds thresholds = scenarioGroups.get(scenarioGroup);
        if (thresholds == null) {
            throw new IllegalArgumentException("Missing thresholds for scenario group " + scenarioGroup);
        }
        return thresholds;
    }

    public record ScenarioGroupThresholds(
            String scenarioGroup,
            double maxDispatchP95Ms,
            double maxDispatchP99Ms,
            double minCompletionRate,
            double minOnTimeRate,
            double minRealAssignmentRate,
            double maxDeadheadDistanceRatio,
            double maxDeadheadDistancePerCompleted,
            double minPostDropOrderHitRate,
            double minDeliveryCorridorQuality,
            double minLastDropGoodZoneRate,
            double maxZigZagPenalty,
            double maxAverageAssignedDeadheadKm,
            double maxFallbackExecutedShare,
            double maxBorrowedCoverageExecutedShare,
            double maxSelectedSubThreeInCleanRate,
            double maxStressDowngradeRate,
            double maxCancellationRate,
            double maxFailedOrderRate,
            Double maxNextOrderIdleMinutes,
            Double maxExpectedPostCompletionEmptyKm
    ) {
        public ScenarioGroupThresholds {
            scenarioGroup = scenarioGroup == null ? "" : scenarioGroup;
            maxDispatchP95Ms = nonNegative(maxDispatchP95Ms);
            maxDispatchP99Ms = nonNegative(maxDispatchP99Ms);
            minCompletionRate = clampPercent(minCompletionRate);
            minOnTimeRate = clampPercent(minOnTimeRate);
            minRealAssignmentRate = clampPercent(minRealAssignmentRate);
            maxDeadheadDistanceRatio = nonNegative(maxDeadheadDistanceRatio);
            maxDeadheadDistancePerCompleted = nonNegative(maxDeadheadDistancePerCompleted);
            minPostDropOrderHitRate = clampPercent(minPostDropOrderHitRate);
            minDeliveryCorridorQuality = Math.max(0.0, minDeliveryCorridorQuality);
            minLastDropGoodZoneRate = clampPercent(minLastDropGoodZoneRate);
            maxZigZagPenalty = Math.max(0.0, maxZigZagPenalty);
            maxAverageAssignedDeadheadKm = nonNegative(maxAverageAssignedDeadheadKm);
            maxFallbackExecutedShare = clampPercent(maxFallbackExecutedShare);
            maxBorrowedCoverageExecutedShare = clampPercent(maxBorrowedCoverageExecutedShare);
            maxSelectedSubThreeInCleanRate = clampPercent(maxSelectedSubThreeInCleanRate);
            maxStressDowngradeRate = clampPercent(maxStressDowngradeRate);
            maxCancellationRate = clampPercent(maxCancellationRate);
            maxFailedOrderRate = clampPercent(maxFailedOrderRate);
            maxNextOrderIdleMinutes = maxNextOrderIdleMinutes == null ? null : nonNegative(maxNextOrderIdleMinutes);
            maxExpectedPostCompletionEmptyKm = maxExpectedPostCompletionEmptyKm == null
                    ? null
                    : nonNegative(maxExpectedPostCompletionEmptyKm);
        }

        private static double clampPercent(double value) {
            return Math.max(0.0, Math.min(100.0, value));
        }

        private static double nonNegative(double value) {
            return Math.max(0.0, value);
        }
    }
}

package com.routechain.simulation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of comparing two simulation runs (baseline vs AI or run A vs run B).
 * All deltas are computed as (runB - runA).
 */
public record ReplayCompareResult(
        String runIdA,
        String runIdB,
        String scenarioA,
        String scenarioB,
        double completionRateDelta,
        double onTimeRateDelta,
        double cancellationRateDelta,
        double deadheadRatioDelta,
        double utilizationDelta,
        double netEarningDelta,
        double bundleRateDelta,
        double etaMAEDelta,
        double assignLatencyDelta,
        double visibleBundleThreePlusRateDelta,
        double deliveryCorridorQualityDelta,
        double lastDropGoodZoneRateDelta,
        double expectedPostCompletionEmptyKmDelta,
        double nextOrderIdleMinutesDelta,
        double zigZagPenaltyAvgDelta,
        double realAssignmentRateDelta,
        double waveAssemblyWaitRateDelta,
        double thirdOrderLaunchRateDelta,
        double cleanWaveRecoveryRateDelta,
        double selectedSubThreeRateInCleanRegimeDelta,
        double stressDowngradeRateDelta,
        double prePickupAugmentRateDelta,
        double holdOnlySelectionRateDelta,
        double nonDowngradedRealAssignmentRateDelta,
        double avgAssignedDeadheadKmDelta,
        double deadheadPerCompletedOrderKmDelta,
        double deadheadPerAssignedOrderKmDelta,
        double postDropOrderHitRateDelta,
        double borrowedDeadheadPerExecutedOrderKmDelta,
        double fallbackDeadheadPerExecutedOrderKmDelta,
        double waveDeadheadPerExecutedOrderKmDelta,
        LatencyBreakdownDelta latencyDelta,
        IntelligenceScorecardDelta intelligenceDelta,
        ScenarioAcceptanceDelta acceptanceDelta,
        String dominantServiceTierA,
        String dominantServiceTierB,
        Map<String, ServiceTierMetricsDelta> serviceTierBreakdownDelta,
        ForecastCalibrationSummaryDelta forecastCalibrationSummaryDelta,
        DispatchRecoveryDecompositionDelta recoveryDelta,
        String verdict,
        double overallGainPercent
) {
    public String schemaVersion() {
        return BenchmarkSchema.VERSION;
    }

    /**
     * Create a comparison from two RunReports.
     */
    public static ReplayCompareResult compare(RunReport baseline, RunReport ai) {
        double compDelta = ai.completionRate() - baseline.completionRate();
        double otDelta = ai.onTimeRate() - baseline.onTimeRate();
        double cancelDelta = ai.cancellationRate() - baseline.cancellationRate();
        double dhDelta = ai.deadheadDistanceRatio() - baseline.deadheadDistanceRatio();
        double utilDelta = ai.avgDriverUtilization() - baseline.avgDriverUtilization();
        double earnDelta = ai.avgNetEarningPerHour() - baseline.avgNetEarningPerHour();
        double bundleDelta = ai.bundleRate() - baseline.bundleRate();
        double etaDelta = ai.etaMAE() - baseline.etaMAE();
        double assignmentLatencyDelta = ai.avgAssignmentLatencyMs() - baseline.avgAssignmentLatencyMs();
        double visibleThreePlusDelta = ai.visibleBundleThreePlusRate() - baseline.visibleBundleThreePlusRate();
        double corridorDelta = ai.deliveryCorridorQuality() - baseline.deliveryCorridorQuality();
        double goodLastDelta = ai.lastDropGoodZoneRate() - baseline.lastDropGoodZoneRate();
        double emptyKmDelta = ai.expectedPostCompletionEmptyKm() - baseline.expectedPostCompletionEmptyKm();
        double nextIdleDelta = ai.nextOrderIdleMinutes() - baseline.nextOrderIdleMinutes();
        double zigZagDelta = ai.zigZagPenaltyAvg() - baseline.zigZagPenaltyAvg();
        double realAssignmentDelta = ai.realAssignmentRate() - baseline.realAssignmentRate();
        double waitDelta = ai.waveAssemblyWaitRate() - baseline.waveAssemblyWaitRate();
        double launchDelta = ai.thirdOrderLaunchRate() - baseline.thirdOrderLaunchRate();
        double recoveryRateDelta = ai.cleanWaveRecoveryRate() - baseline.cleanWaveRecoveryRate();
        double subThreeDelta =
                ai.selectedSubThreeRateInCleanRegime() - baseline.selectedSubThreeRateInCleanRegime();
        double downgradeDelta = ai.stressDowngradeRate() - baseline.stressDowngradeRate();
        double augmentDelta = ai.prePickupAugmentRate() - baseline.prePickupAugmentRate();
        double holdOnlyDelta = ai.holdOnlySelectionRate() - baseline.holdOnlySelectionRate();
        double steadyAssignmentDelta =
                ai.nonDowngradedRealAssignmentRate() - baseline.nonDowngradedRealAssignmentRate();
        double avgAssignedDeadheadKmDelta = ai.avgAssignedDeadheadKm() - baseline.avgAssignedDeadheadKm();
        double deadheadPerCompletedOrderKmDelta =
                ai.deadheadPerCompletedOrderKm() - baseline.deadheadPerCompletedOrderKm();
        double deadheadPerAssignedOrderKmDelta =
                ai.deadheadPerAssignedOrderKm() - baseline.deadheadPerAssignedOrderKm();
        double postDropOrderHitRateDelta =
                ai.postDropOrderHitRate() - baseline.postDropOrderHitRate();
        double borrowedDeadheadPerExecutedOrderKmDelta =
                ai.borrowedDeadheadPerExecutedOrderKm() - baseline.borrowedDeadheadPerExecutedOrderKm();
        double fallbackDeadheadPerExecutedOrderKmDelta =
                ai.fallbackDeadheadPerExecutedOrderKm() - baseline.fallbackDeadheadPerExecutedOrderKm();
        double waveDeadheadPerExecutedOrderKmDelta =
                ai.waveDeadheadPerExecutedOrderKm() - baseline.waveDeadheadPerExecutedOrderKm();
        LatencyBreakdownDelta latencyDelta = LatencyBreakdownDelta.compare(
                baseline.latency(),
                ai.latency());
        IntelligenceScorecardDelta intelligenceDelta = IntelligenceScorecardDelta.compare(
                baseline.intelligence(),
                ai.intelligence());
        ScenarioAcceptanceDelta acceptanceDelta = ScenarioAcceptanceDelta.compare(
                baseline.acceptance(),
                ai.acceptance());
        Map<String, ServiceTierMetricsDelta> serviceTierBreakdownDelta = compareServiceTiers(
                baseline.serviceTierBreakdown(),
                ai.serviceTierBreakdown());
        ForecastCalibrationSummaryDelta forecastCalibrationSummaryDelta =
                ForecastCalibrationSummaryDelta.compare(
                        baseline.forecastCalibrationSummary(),
                        ai.forecastCalibrationSummary());
        DispatchRecoveryDecompositionDelta recoveryFunnelDelta =
                DispatchRecoveryDecompositionDelta.compare(baseline.recovery(), ai.recovery());

        double gain =
                compDelta * 0.20 +
                otDelta * 0.20 +
                (-cancelDelta) * 0.15 +
                (-dhDelta) * 0.15 +
                utilDelta * 100 * 0.10 +
                earnDelta / 1000 * 0.10 +
                (-etaDelta) * 0.06 +
                corridorDelta * 4.0 * 0.02 +
                goodLastDelta * 0.01 +
                visibleThreePlusDelta * 0.005 +
                (-emptyKmDelta) * 0.02 +
                (-nextIdleDelta) * 0.01 +
                (-zigZagDelta) * 6.0 * 0.01 +
                realAssignmentDelta * 0.015 +
                (-waitDelta) * 0.012 +
                launchDelta * 0.010 +
                recoveryRateDelta * 0.010 +
                (-subThreeDelta) * 0.006 +
                (-downgradeDelta) * 0.006 +
                augmentDelta * 0.006 +
                (-holdOnlyDelta) * 0.006 +
                steadyAssignmentDelta * 0.010 +
                (-deadheadPerCompletedOrderKmDelta) * 0.05 +
                postDropOrderHitRateDelta * 0.02 +
                intelligenceDelta.businessScoreDelta() * 2.5 +
                intelligenceDelta.forecastScoreDelta() * 0.8 +
                (-latencyDelta.dispatchP95MsDelta()) * 0.0015;

        String verdict;
        if (gain > 1.0) {
            verdict = "AI_BETTER";
        } else if (gain < -1.0) {
            verdict = "BASELINE_BETTER";
        } else {
            verdict = "MIXED";
        }

        return new ReplayCompareResult(
                baseline.runId(),
                ai.runId(),
                baseline.scenarioName(),
                ai.scenarioName(),
                compDelta,
                otDelta,
                cancelDelta,
                dhDelta,
                utilDelta,
                earnDelta,
                bundleDelta,
                etaDelta,
                assignmentLatencyDelta,
                visibleThreePlusDelta,
                corridorDelta,
                goodLastDelta,
                emptyKmDelta,
                nextIdleDelta,
                zigZagDelta,
                realAssignmentDelta,
                waitDelta,
                launchDelta,
                recoveryRateDelta,
                subThreeDelta,
                downgradeDelta,
                augmentDelta,
                holdOnlyDelta,
                steadyAssignmentDelta,
                avgAssignedDeadheadKmDelta,
                deadheadPerCompletedOrderKmDelta,
                deadheadPerAssignedOrderKmDelta,
                postDropOrderHitRateDelta,
                borrowedDeadheadPerExecutedOrderKmDelta,
                fallbackDeadheadPerExecutedOrderKmDelta,
                waveDeadheadPerExecutedOrderKmDelta,
                latencyDelta,
                intelligenceDelta,
                acceptanceDelta,
                baseline.dominantServiceTier(),
                ai.dominantServiceTier(),
                serviceTierBreakdownDelta,
                forecastCalibrationSummaryDelta,
                recoveryFunnelDelta,
                verdict,
                Math.round(gain * 10) / 10.0
        );
    }

    public boolean improvesRecoveryBehavior() {
        return realAssignmentRateDelta > 0.0
                && nonDowngradedRealAssignmentRateDelta >= 0.0
                && waveAssemblyWaitRateDelta < 0.0
                && cleanWaveRecoveryRateDelta >= 0.0
                && stressDowngradeRateDelta <= 0.0
                && holdOnlySelectionRateDelta <= 0.0;
    }

    public String recoveryControlPlaneSummary() {
        return String.format(
                "realAssign=%+.1fpp steadyAssign=%+.1fpp wait3=%+.1fpp launch3=%+.1fpp downgrade=%+.1fpp augment=%+.1fpp holdOnly=%+.1fpp recover3=%+.1fpp",
                realAssignmentRateDelta,
                nonDowngradedRealAssignmentRateDelta,
                waveAssemblyWaitRateDelta,
                thirdOrderLaunchRateDelta,
                stressDowngradeRateDelta,
                prePickupAugmentRateDelta,
                holdOnlySelectionRateDelta,
                cleanWaveRecoveryRateDelta
        );
    }

    public String toSummary() {
        return String.format(
                "[Replay] %s vs %s | verdict=%s gain=%.1f%% | "
                        + "completion=%+.1f%% onTime=%+.1f%% cancel=%+.1f%% deadhead=%+.1f%% util=%+.2f | "
                        + "3plus=%+.1fpp corridor=%+.2f goodLast=%+.1fpp emptyKm=%+.2f | "
                        + "realAssign=%+.1fpp steadyAssign=%+.1fpp wait3=%+.1fpp launch3=%+.1fpp recover3=%+.1fpp sub3=%+.1fpp downgrade=%+.1fpp augment=%+.1fpp holdOnly=%+.1fpp | "
                        + "tier=%s->%s waveExec=%+d holdConv=%+d fallbackDirect=%+d borrowedExec=%+d dh/completed=%+.2fkm postDropHit=%+.1fpp prepMae=%+.2fm contGap=%+.2f dispatchP95=%+.1fms biz=%+.2f bal=%+.2f",
                scenarioA,
                scenarioB,
                verdict,
                overallGainPercent,
                completionRateDelta,
                onTimeRateDelta,
                cancellationRateDelta,
                deadheadRatioDelta,
                utilizationDelta,
                visibleBundleThreePlusRateDelta,
                deliveryCorridorQualityDelta,
                lastDropGoodZoneRateDelta,
                expectedPostCompletionEmptyKmDelta,
                realAssignmentRateDelta,
                nonDowngradedRealAssignmentRateDelta,
                waveAssemblyWaitRateDelta,
                thirdOrderLaunchRateDelta,
                cleanWaveRecoveryRateDelta,
                selectedSubThreeRateInCleanRegimeDelta,
                stressDowngradeRateDelta,
                prePickupAugmentRateDelta,
                holdOnlySelectionRateDelta,
                dominantServiceTierA,
                dominantServiceTierB,
                recoveryDelta == null ? 0 : recoveryDelta.executedWaveCountDelta(),
                recoveryDelta == null ? 0 : recoveryDelta.holdConvertedToWaveCountDelta(),
                recoveryDelta == null ? 0 : recoveryDelta.executedFallbackCountDelta(),
                recoveryDelta == null ? 0 : recoveryDelta.executedBorrowedCountDelta(),
                deadheadPerCompletedOrderKmDelta,
                postDropOrderHitRateDelta,
                forecastCalibrationSummaryDelta == null ? 0.0 : forecastCalibrationSummaryDelta.merchantPrepMaeMinutesDelta(),
                forecastCalibrationSummaryDelta == null ? 0.0 : forecastCalibrationSummaryDelta.continuationCalibrationGapDelta(),
                latencyDelta == null ? 0.0 : latencyDelta.dispatchP95MsDelta(),
                intelligenceDelta == null ? 0.0 : intelligenceDelta.businessScoreDelta(),
                intelligenceDelta == null ? 0.0 : ((intelligenceDelta.businessScoreDelta()
                        + intelligenceDelta.routingScoreDelta()
                        + intelligenceDelta.networkScoreDelta()
                        + intelligenceDelta.forecastScoreDelta()) / 4.0)
        );
    }

    private static Map<String, ServiceTierMetricsDelta> compareServiceTiers(
            Map<String, ServiceTierMetrics> baseline,
            Map<String, ServiceTierMetrics> candidate) {
        Map<String, ServiceTierMetrics> safeBaseline = baseline == null ? Map.of() : baseline;
        Map<String, ServiceTierMetrics> safeCandidate = candidate == null ? Map.of() : candidate;
        Map<String, ServiceTierMetricsDelta> delta = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(safeBaseline.keySet());
        keys.addAll(safeCandidate.keySet());
        for (String key : keys) {
            ServiceTierMetrics left = safeBaseline.get(key);
            ServiceTierMetrics right = safeCandidate.get(key);
            delta.put(key, new ServiceTierMetricsDelta(
                    key,
                    (right == null ? 0 : right.orderCount()) - (left == null ? 0 : left.orderCount()),
                    (right == null ? 0 : right.completedOrderCount()) - (left == null ? 0 : left.completedOrderCount()),
                    (right == null ? 0.0 : right.completionRate()) - (left == null ? 0.0 : left.completionRate()),
                    (right == null ? 0.0 : right.avgPromisedEtaMinutes()) - (left == null ? 0.0 : left.avgPromisedEtaMinutes()),
                    (right == null ? 0.0 : right.avgQuotedFee()) - (left == null ? 0.0 : left.avgQuotedFee())
            ));
        }
        return delta;
    }
}

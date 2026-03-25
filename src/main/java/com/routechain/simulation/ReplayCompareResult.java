package com.routechain.simulation;

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
        String verdict,
        double overallGainPercent
) {
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
        double latencyDelta = ai.avgAssignmentLatencyMs() - baseline.avgAssignmentLatencyMs();
        double visibleThreePlusDelta = ai.visibleBundleThreePlusRate() - baseline.visibleBundleThreePlusRate();
        double corridorDelta = ai.deliveryCorridorQuality() - baseline.deliveryCorridorQuality();
        double goodLastDelta = ai.lastDropGoodZoneRate() - baseline.lastDropGoodZoneRate();
        double emptyKmDelta = ai.expectedPostCompletionEmptyKm() - baseline.expectedPostCompletionEmptyKm();
        double nextIdleDelta = ai.nextOrderIdleMinutes() - baseline.nextOrderIdleMinutes();
        double zigZagDelta = ai.zigZagPenaltyAvg() - baseline.zigZagPenaltyAvg();
        double realAssignmentDelta = ai.realAssignmentRate() - baseline.realAssignmentRate();
        double waitDelta = ai.waveAssemblyWaitRate() - baseline.waveAssemblyWaitRate();
        double launchDelta = ai.thirdOrderLaunchRate() - baseline.thirdOrderLaunchRate();
        double recoveryDelta = ai.cleanWaveRecoveryRate() - baseline.cleanWaveRecoveryRate();
        double subThreeDelta =
                ai.selectedSubThreeRateInCleanRegime() - baseline.selectedSubThreeRateInCleanRegime();
        double downgradeDelta = ai.stressDowngradeRate() - baseline.stressDowngradeRate();
        double augmentDelta = ai.prePickupAugmentRate() - baseline.prePickupAugmentRate();
        double holdOnlyDelta = ai.holdOnlySelectionRate() - baseline.holdOnlySelectionRate();
        double steadyAssignmentDelta =
                ai.nonDowngradedRealAssignmentRate() - baseline.nonDowngradedRealAssignmentRate();

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
                recoveryDelta * 0.010 +
                (-subThreeDelta) * 0.006 +
                (-downgradeDelta) * 0.006 +
                augmentDelta * 0.006 +
                (-holdOnlyDelta) * 0.006 +
                steadyAssignmentDelta * 0.010;

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
                latencyDelta,
                visibleThreePlusDelta,
                corridorDelta,
                goodLastDelta,
                emptyKmDelta,
                nextIdleDelta,
                zigZagDelta,
                realAssignmentDelta,
                waitDelta,
                launchDelta,
                recoveryDelta,
                subThreeDelta,
                downgradeDelta,
                augmentDelta,
                holdOnlyDelta,
                steadyAssignmentDelta,
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
                        + "realAssign=%+.1fpp steadyAssign=%+.1fpp wait3=%+.1fpp launch3=%+.1fpp recover3=%+.1fpp sub3=%+.1fpp downgrade=%+.1fpp augment=%+.1fpp holdOnly=%+.1fpp",
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
                holdOnlySelectionRateDelta
        );
    }
}

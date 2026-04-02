package com.routechain.simulation;

import java.time.Instant;
import java.util.Map;

/**
 * Captures all KPIs for a single simulation run.
 * Used for reporting, replay comparison, and analysis.
 */
public record RunReport(
        // ── Run identity ────────────────────────────────────────────────
        String runId,
        String scenarioName,
        long seed,
        Instant startTime,
        Instant endTime,
        long totalTicks,

        // ── Volume ──────────────────────────────────────────────────────
        int totalOrders,
        int totalDrivers,

        // ── KPI: Operations ─────────────────────────────────────────────
        double completionRate,          // %
        double onTimeRate,              // %
        double cancellationRate,        // %
        double failedOrderRate,         // %

        // ── KPI: Driver efficiency ──────────────────────────────────────
        double deadheadDistanceRatio,   // %
        double deadheadTimeRatio,       // %
        double avgDriverUtilization,    // 0-1
        double avgOrdersPerDriverPerHour,
        double avgNetEarningPerHour,    // VND

        // ── KPI: Bundle / Route ─────────────────────────────────────────
        double bundleRate,              // % of orders bundled
        double bundleSuccessRate,       // % bundles completed without SLA violation
        double avgObservedBundleSize,
        int maxObservedBundleSize,
        double bundleThreePlusRate,
        int reDispatchCount,

        // ── KPI: AI ─────────────────────────────────────────────────────
        double avgAssignmentLatencyMs,
        double avgConfidence,
        double etaMAE,                  // ETA mean absolute error (minutes)

        // ── KPI: System ─────────────────────────────────────────────────
        int surgeEventsDetected,
        int shortageEventsDetected,
        double avgFeePerOrder,          // VND

        // ── KPI: Route landing / empty-mile quality ────────────────────
        double visibleBundleThreePlusRate,
        double lastDropGoodZoneRate,
        double expectedPostCompletionEmptyKm,
        double nextOrderIdleMinutes,
        double deliveryCorridorQuality,
        double zigZagPenaltyAvg,
        double realAssignmentRate,
        double selectedSubThreeRateInCleanRegime,
        double waveAssemblyWaitRate,
        double thirdOrderLaunchRate,
        double stressDowngradeRate,
        double prePickupAugmentRate,
        double holdOnlySelectionRate,
        double avgAssignedDeadheadKm,
        double deadheadPerCompletedOrderKm,
        double deadheadPerAssignedOrderKm,
        double postDropOrderHitRate,
        double borrowedDeadheadPerExecutedOrderKm,
        double fallbackDeadheadPerExecutedOrderKm,
        double waveDeadheadPerExecutedOrderKm,
        LatencyBreakdown latency,
        IntelligenceScorecard intelligence,
        ScenarioAcceptanceResult acceptance,
        String dominantServiceTier,
        Map<String, ServiceTierMetrics> serviceTierBreakdown,
        ForecastCalibrationSummary forecastCalibrationSummary,
        DispatchRecoveryDecomposition recovery
) {
    public RunReport {
        if (recovery == null) {
            recovery = DispatchRecoveryDecomposition.empty();
        }
        latency = latency == null ? LatencyBreakdown.empty() : latency;
        intelligence = intelligence == null ? IntelligenceScorecard.empty() : intelligence;
        acceptance = acceptance == null ? ScenarioAcceptanceResult.empty() : acceptance;
        dominantServiceTier = dominantServiceTier == null || dominantServiceTier.isBlank()
                ? "instant"
                : dominantServiceTier;
        serviceTierBreakdown = serviceTierBreakdown == null
                ? Map.of()
                : Map.copyOf(serviceTierBreakdown);
        forecastCalibrationSummary = forecastCalibrationSummary == null
                ? ForecastCalibrationSummary.empty()
                : forecastCalibrationSummary;
    }

    public String schemaVersion() {
        return BenchmarkSchema.VERSION;
    }

    public double holdBehaviorRate() {
        return clampPercent(holdOnlySelectionRate);
    }

    public double augmentBehaviorRate() {
        return clampPercent(prePickupAugmentRate);
    }

    public double nonDowngradedRealAssignmentRate() {
        return clampPercent(realAssignmentRate - stressDowngradeRate);
    }

    public double cleanWaveRecoveryRate() {
        double assigned = realAssignmentRate;
        if (assigned <= 0.0) {
            return 0.0;
        }
        return clampPercent(thirdOrderLaunchRate * 100.0 / assigned);
    }

    public String recoveryControlPlaneSummary() {
        return String.format(
                "realAssign=%.1f%% steadyAssign=%.1f%% wait3=%.1f%% launch3=%.1f%% downgrade=%.1f%% augment=%.1f%% holdOnly=%.1f%% recover3=%.1f%%",
                realAssignmentRate,
                nonDowngradedRealAssignmentRate(),
                waveAssemblyWaitRate,
                thirdOrderLaunchRate,
                stressDowngradeRate,
                prePickupAugmentRate,
                holdOnlySelectionRate,
                cleanWaveRecoveryRate()
        );
    }

    public double waveExec() {
        return recovery.waveExecutionRate();
    }

    public double holdConv() {
        return recovery.holdConversionRate();
    }

    public double fallbackDirect() {
        return recovery.fallbackDirectRate();
    }

    public double borrowedExec() {
        return recovery.borrowedSelectionRate();
    }

    /** Generate a compact summary string for logging. */
    public String toSummary() {
        return String.format(
                "[RunReport] %s | scenario=%s | orders=%d drivers=%d | " +
                "completion=%.1f%% onTime=%.1f%% cancel=%.1f%% | " +
                "deadhead=%.1f%% utilization=%.1f%% | " +
                "bundleRate=%.1f%% avgBundle=%.2f maxBundle=%d 3plus=%.1f%% reDispatch=%d | " +
                "corridor=%.2f goodLast=%.1f%% emptyKm=%.2f | " +
                "realAssign=%.1f%% steadyAssign=%.1f%% cleanSub3=%.1f%% wait3=%.1f%% launch3=%.1f%% downgrade=%.1f%% augment=%.1f%% holdOnly=%.1f%% | " +
                "tier=%s dh/assigned=%.2fkm dh/completed=%.2fkm postDropHit=%.1f%% waveExec=%.1f%% holdConv=%.1f%% fallbackDirect=%.1f%% borrowedExec=%.1f%% | " +
                "prepMae=%.2fm contGap=%.2f dispatchP95=%.1fms biz=%s bal=%s | " +
                "recover3=%.1f%% | " +
                "assignLatency=%.0fms confidence=%.2f",
                runId, scenarioName, totalOrders, totalDrivers,
                completionRate, onTimeRate, cancellationRate,
                deadheadDistanceRatio, avgDriverUtilization * 100,
                bundleRate, avgObservedBundleSize, maxObservedBundleSize,
                visibleBundleThreePlusRate, reDispatchCount,
                deliveryCorridorQuality, lastDropGoodZoneRate, expectedPostCompletionEmptyKm,
                realAssignmentRate, nonDowngradedRealAssignmentRate(), selectedSubThreeRateInCleanRegime, waveAssemblyWaitRate,
                thirdOrderLaunchRate, stressDowngradeRate, prePickupAugmentRate, holdOnlySelectionRate,
                dominantServiceTier,
                deadheadPerAssignedOrderKm, deadheadPerCompletedOrderKm, postDropOrderHitRate,
                recovery == null ? 0.0 : recovery.waveExecutionRate(),
                recovery == null ? 0.0 : recovery.holdConversionRate(),
                recovery == null ? 0.0 : recovery.fallbackDirectRate(),
                recovery == null ? 0.0 : recovery.borrowedSelectionRate(),
                forecastCalibrationSummary == null ? 0.0 : forecastCalibrationSummary.merchantPrepMaeMinutes(),
                forecastCalibrationSummary == null ? 0.0 : forecastCalibrationSummary.continuationCalibrationGap(),
                latency == null ? 0.0 : latency.dispatchP95Ms(),
                intelligence == null ? "UNASSESSED" : intelligence.primaryVerdict(),
                intelligence == null ? "UNASSESSED" : intelligence.secondaryVerdict(),
                cleanWaveRecoveryRate(),
                avgAssignmentLatencyMs, avgConfidence
        );
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}

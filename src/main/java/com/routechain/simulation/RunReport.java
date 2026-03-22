package com.routechain.simulation;

import java.time.Instant;

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
        int reDispatchCount,

        // ── KPI: AI ─────────────────────────────────────────────────────
        double avgAssignmentLatencyMs,
        double avgConfidence,
        double etaMAE,                  // ETA mean absolute error (minutes)

        // ── KPI: System ─────────────────────────────────────────────────
        int surgeEventsDetected,
        int shortageEventsDetected,
        double avgFeePerOrder           // VND
) {
    /** Generate a compact summary string for logging. */
    public String toSummary() {
        return String.format(
                "[RunReport] %s | scenario=%s | orders=%d drivers=%d | " +
                "completion=%.1f%% onTime=%.1f%% cancel=%.1f%% | " +
                "deadhead=%.1f%% utilization=%.1f%% | " +
                "bundleRate=%.1f%% reDispatch=%d | " +
                "assignLatency=%.0fms confidence=%.2f",
                runId, scenarioName, totalOrders, totalDrivers,
                completionRate, onTimeRate, cancellationRate,
                deadheadDistanceRatio, avgDriverUtilization * 100,
                bundleRate, reDispatchCount,
                avgAssignmentLatencyMs, avgConfidence
        );
    }
}

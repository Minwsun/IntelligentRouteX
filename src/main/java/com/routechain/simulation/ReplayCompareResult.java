package com.routechain.simulation;

/**
 * Result of comparing two simulation runs (baseline vs AI or run A vs run B).
 * All deltas are computed as (runB - runA), so positive means B is better
 * for metrics where higher is better, and negative means B is better
 * for metrics where lower is better.
 */
public record ReplayCompareResult(
        // ── Run references ──────────────────────────────────────────────
        String runIdA,
        String runIdB,
        String scenarioA,
        String scenarioB,

        // ── Deltas (B - A) ──────────────────────────────────────────────
        double completionRateDelta,     // +2.5 means B is 2.5% better
        double onTimeRateDelta,
        double cancellationRateDelta,   // negative is better for B
        double deadheadRatioDelta,      // negative is better for B
        double utilizationDelta,
        double netEarningDelta,
        double bundleRateDelta,
        double etaMAEDelta,             // negative is better for B
        double assignLatencyDelta,      // negative is better for B

        // ── Verdict ─────────────────────────────────────────────────────
        String verdict,                 // "AI_BETTER", "BASELINE_BETTER", "MIXED"
        double overallGainPercent       // weighted overall improvement %
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

        // Weighted gain: positive metrics use positive delta, negative metrics invert
        double gain =
                compDelta * 0.20 +         // completion better
                otDelta * 0.20 +           // on-time better
                (-cancelDelta) * 0.15 +    // less cancellation
                (-dhDelta) * 0.15 +        // less deadhead
                utilDelta * 100 * 0.10 +   // better utilization
                earnDelta / 1000 * 0.10 +  // better earnings
                (-etaDelta) * 0.10;        // less ETA error

        String verdict;
        if (gain > 1.0) verdict = "AI_BETTER";
        else if (gain < -1.0) verdict = "BASELINE_BETTER";
        else verdict = "MIXED";

        return new ReplayCompareResult(
                baseline.runId(), ai.runId(),
                baseline.scenarioName(), ai.scenarioName(),
                compDelta, otDelta, cancelDelta, dhDelta,
                utilDelta, earnDelta, bundleDelta, etaDelta, latencyDelta,
                verdict, Math.round(gain * 10) / 10.0
        );
    }

    /** Generate summary for UI display. */
    public String toSummary() {
        return String.format(
                "[Replay] %s vs %s | verdict=%s gain=%.1f%% | " +
                "completion=%+.1f%% onTime=%+.1f%% cancel=%+.1f%% deadhead=%+.1f%% util=%+.2f",
                scenarioA, scenarioB, verdict, overallGainPercent,
                completionRateDelta, onTimeRateDelta,
                cancellationRateDelta, deadheadRatioDelta, utilizationDelta
        );
    }
}

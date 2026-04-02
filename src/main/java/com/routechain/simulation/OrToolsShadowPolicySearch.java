package com.routechain.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * OR-Tools shadow evaluator for offline challenger objective comparison.
 *
 * This runner does not replace the hot path; it computes an objective proxy
 * from completed run reports and can be swapped with native OR-Tools search later.
 */
public final class OrToolsShadowPolicySearch {
    private final boolean orToolsRuntimeAvailable;

    public OrToolsShadowPolicySearch() {
        this.orToolsRuntimeAvailable = isClassAvailable("com.google.ortools.Loader");
    }

    public String mode() {
        return orToolsRuntimeAvailable ? "ortools-shadow-runtime" : "ortools-shadow-proxy";
    }

    public BenchmarkStatSummary summarize(String scope, List<RunReport> reports) {
        List<Double> objectives = new ArrayList<>();
        if (reports != null) {
            for (RunReport report : reports) {
                objectives.add(objective(report));
            }
        }
        return BenchmarkStatistics.summarize("ortoolsShadowObjective", scope + "/" + mode(), objectives);
    }

    public double objective(RunReport report) {
        if (report == null) {
            return 0.0;
        }
        // Maximize completion/on-time/launch3, minimize deadhead/cancel/latency.
        return report.completionRate() * 0.34
                + report.onTimeRate() * 0.20
                - report.cancellationRate() * 0.14
                - report.deadheadDistanceRatio() * 0.14
                + report.thirdOrderLaunchRate() * 0.08
                - report.avgAssignmentLatencyMs() / 1000.0 * 0.06
                - report.deadheadPerCompletedOrderKm() * 0.04;
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}

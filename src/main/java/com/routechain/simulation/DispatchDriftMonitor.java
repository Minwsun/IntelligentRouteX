package com.routechain.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight drift monitor for benchmark windows.
 */
public final class DispatchDriftMonitor {
    private DispatchDriftMonitor() {}

    public static List<DispatchDriftSnapshot> evaluate(String scope,
                                                       List<RunReport> baseline,
                                                       List<RunReport> candidate) {
        List<DispatchDriftSnapshot> snapshots = new ArrayList<>();
        double baseCompletion = mean(baseline, RunReport::completionRate);
        double candCompletion = mean(candidate, RunReport::completionRate);
        snapshots.add(snapshot(scope, "completionRate", baseCompletion, candCompletion, 2.0));

        double baseDeadhead = mean(baseline, RunReport::deadheadDistanceRatio);
        double candDeadhead = mean(candidate, RunReport::deadheadDistanceRatio);
        snapshots.add(snapshot(scope, "deadheadDistanceRatio", baseDeadhead, candDeadhead, 3.0));

        double baseLatency = mean(baseline, RunReport::avgAssignmentLatencyMs);
        double candLatency = mean(candidate, RunReport::avgAssignmentLatencyMs);
        snapshots.add(snapshot(scope, "avgAssignmentLatencyMs", baseLatency, candLatency, 120.0));
        return snapshots;
    }

    private static DispatchDriftSnapshot snapshot(String scope,
                                                  String metricName,
                                                  double baselineMean,
                                                  double candidateMean,
                                                  double threshold) {
        double drift = candidateMean - baselineMean;
        boolean drifted;
        if ("completionRate".equals(metricName)) {
            drifted = drift < -threshold;
        } else {
            drifted = Math.abs(drift) > threshold;
        }
        return new DispatchDriftSnapshot(
                BenchmarkSchema.VERSION,
                scope,
                metricName,
                baselineMean,
                candidateMean,
                drift,
                threshold,
                drifted
        );
    }

    private static double mean(List<RunReport> reports, java.util.function.ToDoubleFunction<RunReport> fn) {
        if (reports == null || reports.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (RunReport report : reports) {
            sum += fn.applyAsDouble(report);
        }
        return sum / reports.size();
    }
}

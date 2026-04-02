package com.routechain.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Guards benchmark instrumentation from mixing runtime latency with business wait time.
 */
public record MeasurementSanityCheck(
        boolean valid,
        List<String> warnings
) {
    public MeasurementSanityCheck {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static MeasurementSanityCheck evaluate(LatencyBreakdown latency,
                                                  double avgAssignmentAgingMs) {
        LatencyBreakdown safe = latency == null ? LatencyBreakdown.empty() : latency;
        List<String> warnings = new ArrayList<>();
        boolean valid = true;
        if (safe.dispatchSampleCount() <= 0) {
            warnings.add("missing dispatch decision latency samples");
            valid = false;
        }
        if (safe.dispatchP50Ms() > safe.dispatchP95Ms() || safe.dispatchP95Ms() > safe.dispatchP99Ms()) {
            warnings.add("dispatch latency percentiles are not monotonic");
            valid = false;
        }
        if (safe.dispatchP95Ms() > 10_000.0) {
            warnings.add("dispatch decision p95 exceeds 10s and likely indicates broken instrumentation or runaway hot path");
            valid = false;
        }
        if (avgAssignmentAgingMs > 0.0 && safe.dispatchP95Ms() > 0.0 && avgAssignmentAgingMs > safe.dispatchP95Ms() * 5.0) {
            warnings.add("assignment aging is much larger than runtime dispatch latency; do not use business wait time for SLO verdicts");
        }
        return new MeasurementSanityCheck(valid, warnings);
    }
}

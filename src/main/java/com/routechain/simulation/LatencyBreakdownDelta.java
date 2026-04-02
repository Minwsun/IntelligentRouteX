package com.routechain.simulation;

/**
 * Delta between two latency profiles.
 */
public record LatencyBreakdownDelta(
        double avgDispatchDecisionLatencyMsDelta,
        double dispatchP50MsDelta,
        double dispatchP95MsDelta,
        double dispatchP99MsDelta,
        double modelP50MsDelta,
        double modelP95MsDelta,
        double neuralPriorP50MsDelta,
        double neuralPriorP95MsDelta,
        double assignmentAgingP50MsDelta,
        double assignmentAgingP95MsDelta,
        double tickThroughputPerSecDelta
) {
    public static LatencyBreakdownDelta compare(LatencyBreakdown baseline,
                                                LatencyBreakdown candidate) {
        LatencyBreakdown left = baseline == null ? LatencyBreakdown.empty() : baseline;
        LatencyBreakdown right = candidate == null ? LatencyBreakdown.empty() : candidate;
        return new LatencyBreakdownDelta(
                right.avgDispatchDecisionLatencyMs() - left.avgDispatchDecisionLatencyMs(),
                right.dispatchP50Ms() - left.dispatchP50Ms(),
                right.dispatchP95Ms() - left.dispatchP95Ms(),
                right.dispatchP99Ms() - left.dispatchP99Ms(),
                right.modelP50Ms() - left.modelP50Ms(),
                right.modelP95Ms() - left.modelP95Ms(),
                right.neuralPriorP50Ms() - left.neuralPriorP50Ms(),
                right.neuralPriorP95Ms() - left.neuralPriorP95Ms(),
                right.assignmentAgingP50Ms() - left.assignmentAgingP50Ms(),
                right.assignmentAgingP95Ms() - left.assignmentAgingP95Ms(),
                right.tickThroughputPerSec() - left.tickThroughputPerSec()
        );
    }
}

package com.routechain.ai;

/**
 * Retrieval-based analog summary for similar historical route decisions.
 */
public record RetrievedRouteAnalogs(
        double analogScore,
        double confidence,
        double worstCasePenalty,
        int sampleCount
) {
    public static RetrievedRouteAnalogs empty() {
        return new RetrievedRouteAnalogs(0.0, 0.0, 0.0, 0);
    }
}

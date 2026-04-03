package com.routechain.graph;

/**
 * Explanation payload for graph-informed route and dispatch decisions.
 */
public record GraphExplanationTrace(
        String traceId,
        String driverId,
        String orderKey,
        String sourceCellId,
        String targetCellId,
        double graphAffinityScore,
        double topologyScore,
        double bundleCompatibilityScore,
        double futureCellScore,
        double congestionPropagationScore,
        String explanation
) {
    public GraphExplanationTrace {
        traceId = traceId == null || traceId.isBlank() ? "graph-trace-unknown" : traceId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        orderKey = orderKey == null || orderKey.isBlank() ? "order-unknown" : orderKey;
        sourceCellId = sourceCellId == null || sourceCellId.isBlank() ? "cell-unknown" : sourceCellId;
        targetCellId = targetCellId == null || targetCellId.isBlank() ? "cell-unknown" : targetCellId;
        explanation = explanation == null ? "" : explanation;
    }
}

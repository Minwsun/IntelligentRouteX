package com.routechain.graph;

/**
 * One weighted relationship in the graph shadow plane.
 */
public record GraphAffinitySnapshot(
        String relationType,
        GraphNodeRef source,
        GraphNodeRef target,
        double affinityScore,
        String explanation
) {
    public GraphAffinitySnapshot {
        relationType = relationType == null || relationType.isBlank() ? "RELATED_TO" : relationType;
        explanation = explanation == null ? "" : explanation;
    }
}

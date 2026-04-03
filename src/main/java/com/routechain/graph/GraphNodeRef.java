package com.routechain.graph;

/**
 * Typed node reference inside the graph shadow plane.
 */
public record GraphNodeRef(
        String nodeType,
        String nodeId,
        String label,
        String cellId,
        double lat,
        double lng
) {
    public GraphNodeRef {
        nodeType = nodeType == null || nodeType.isBlank() ? "UNKNOWN" : nodeType;
        nodeId = nodeId == null || nodeId.isBlank() ? "node-unknown" : nodeId;
        label = label == null ? "" : label;
        cellId = cellId == null || cellId.isBlank() ? "cell-unknown" : cellId;
    }
}

package com.routechain.v2.selector;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record ConflictGraph(
        String schemaVersion,
        int candidateCount,
        int conflictEdgeCount,
        List<ConflictEdge> edges) implements SchemaVersioned {

    public static ConflictGraph empty() {
        return new ConflictGraph("conflict-graph/v1", 0, 0, List.of());
    }
}

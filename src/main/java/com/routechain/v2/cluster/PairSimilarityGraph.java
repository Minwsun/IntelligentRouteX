package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record PairSimilarityGraph(
        String schemaVersion,
        int orderCount,
        int edgeCount,
        List<PairEdge> edges) implements SchemaVersioned {
}


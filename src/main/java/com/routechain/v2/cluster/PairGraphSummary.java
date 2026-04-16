package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record PairGraphSummary(
        String schemaVersion,
        int candidatePairCount,
        int gatedPairCount,
        int edgeCount,
        double averageEdgeWeight,
        List<String> degradeReasons) implements SchemaVersioned {

    public static PairGraphSummary empty() {
        return new PairGraphSummary("pair-graph-summary/v1", 0, 0, 0, 0.0, List.of());
    }
}


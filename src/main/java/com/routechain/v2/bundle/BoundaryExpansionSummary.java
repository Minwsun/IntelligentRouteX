package com.routechain.v2.bundle;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record BoundaryExpansionSummary(
        String schemaVersion,
        int clusterCount,
        int expandedClusterCount,
        int acceptedBoundaryOrderCount,
        int rejectedBoundaryOrderCount,
        List<String> degradeReasons) implements SchemaVersioned {

    public static BoundaryExpansionSummary empty() {
        return new BoundaryExpansionSummary("boundary-expansion-summary/v1", 0, 0, 0, 0, List.of());
    }
}

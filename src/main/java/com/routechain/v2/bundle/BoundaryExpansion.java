package com.routechain.v2.bundle;

import com.routechain.v2.SchemaVersioned;

import java.util.List;
import java.util.Map;

public record BoundaryExpansion(
        String schemaVersion,
        String clusterId,
        List<String> coreOrderIds,
        List<String> acceptedBoundaryOrderIds,
        List<String> rejectedBoundaryOrderIds,
        Map<String, Double> supportScoreByOrder,
        List<String> expansionReasons,
        boolean weatherTightened) implements SchemaVersioned {
}

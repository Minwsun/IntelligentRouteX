package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record MicroClusterSummary(
        String schemaVersion,
        int clusterCount,
        int largestClusterSize,
        int singletonCount,
        List<String> degradeReasons) implements SchemaVersioned {

    public static MicroClusterSummary empty() {
        return new MicroClusterSummary("micro-cluster-summary/v1", 0, 0, 0, List.of());
    }
}


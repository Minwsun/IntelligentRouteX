package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchPairClusterStage(
        String schemaVersion,
        BufferedOrderWindow bufferedOrderWindow,
        PairGraphSummary pairGraphSummary,
        List<MicroCluster> microClusters,
        MicroClusterSummary microClusterSummary,
        List<String> degradeReasons) implements SchemaVersioned {
}

package com.routechain.v2.cluster;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.HotStartReuseSummary;

import java.util.List;

public record DispatchPairClusterStage(
        String schemaVersion,
        BufferedOrderWindow bufferedOrderWindow,
        PairGraphSummary pairGraphSummary,
        PairSimilarityGraph pairSimilarityGraph,
        List<MicroCluster> microClusters,
        MicroClusterSummary microClusterSummary,
        HotStartReuseSummary hotStartReuseSummary,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}

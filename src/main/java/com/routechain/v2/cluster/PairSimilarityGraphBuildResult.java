package com.routechain.v2.cluster;

import com.routechain.v2.MlStageMetadata;

import java.util.List;

public record PairSimilarityGraphBuildResult(
        PairSimilarityGraph graph,
        int candidatePairCount,
        int gatedPairCount,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) {
}

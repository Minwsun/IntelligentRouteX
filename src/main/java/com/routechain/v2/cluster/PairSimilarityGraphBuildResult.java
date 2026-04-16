package com.routechain.v2.cluster;

import java.util.List;

public record PairSimilarityGraphBuildResult(
        PairSimilarityGraph graph,
        int candidatePairCount,
        int gatedPairCount,
        List<String> degradeReasons) {
}

package com.routechain.v2.cluster;

import com.routechain.v2.integration.TabularScoreResult;

record PairScoringTrace(
        PairFeatureVector featureVector,
        PairGateDecision gateDecision,
        double deterministicScore,
        Double tabularScore,
        PairCompatibility compatibility,
        TabularScoreResult tabularScoreResult) {
}

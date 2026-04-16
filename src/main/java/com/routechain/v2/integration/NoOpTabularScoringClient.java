package com.routechain.v2.integration;

import com.routechain.v2.context.EtaFeatureVector;
import com.routechain.v2.cluster.PairFeatureVector;

public final class NoOpTabularScoringClient implements TabularScoringClient {
    @Override
    public TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied();
    }

    @Override
    public TabularScoreResult scorePair(PairFeatureVector pairFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied();
    }
}

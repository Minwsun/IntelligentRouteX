package com.routechain.v2.integration;

import com.routechain.v2.context.EtaFeatureVector;
import com.routechain.v2.cluster.PairFeatureVector;

public interface TabularScoringClient {
    TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs);

    TabularScoreResult scorePair(PairFeatureVector pairFeatureVector, long timeoutBudgetMs);
}

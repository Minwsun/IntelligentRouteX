package com.routechain.v2.integration;

import com.routechain.v2.context.EtaFeatureVector;

public interface TabularScoringClient {
    TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs);
}


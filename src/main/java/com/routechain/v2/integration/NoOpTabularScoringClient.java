package com.routechain.v2.integration;

import com.routechain.v2.context.EtaFeatureVector;

public final class NoOpTabularScoringClient implements TabularScoringClient {
    @Override
    public TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied();
    }
}


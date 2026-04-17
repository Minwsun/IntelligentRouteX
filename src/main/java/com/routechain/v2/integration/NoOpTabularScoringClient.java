package com.routechain.v2.integration;

import com.routechain.v2.context.EtaFeatureVector;
import com.routechain.v2.cluster.PairFeatureVector;
import com.routechain.v2.route.DriverFitFeatureVector;
import com.routechain.v2.route.RouteValueFeatureVector;

public final class NoOpTabularScoringClient implements TabularScoringClient {
    @Override
    public TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied("tabular-client-disabled");
    }

    @Override
    public TabularScoreResult scorePair(PairFeatureVector pairFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied("tabular-client-disabled");
    }

    @Override
    public TabularScoreResult scoreDriverFit(DriverFitFeatureVector driverFitFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied("tabular-client-disabled");
    }

    @Override
    public TabularScoreResult scoreRouteValue(RouteValueFeatureVector routeValueFeatureVector, long timeoutBudgetMs) {
        return TabularScoreResult.notApplied("tabular-client-disabled");
    }

    @Override
    public WorkerReadyState readyState() {
        return WorkerReadyState.notReady("tabular-client-disabled", MlWorkerMetadata.empty());
    }
}

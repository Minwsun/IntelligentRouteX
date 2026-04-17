package com.routechain.v2.integration;

import com.routechain.v2.context.EtaFeatureVector;
import com.routechain.v2.cluster.PairFeatureVector;
import com.routechain.v2.route.DriverFitFeatureVector;
import com.routechain.v2.route.RouteValueFeatureVector;

public interface TabularScoringClient {
    TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs);

    TabularScoreResult scorePair(PairFeatureVector pairFeatureVector, long timeoutBudgetMs);

    TabularScoreResult scoreDriverFit(DriverFitFeatureVector driverFitFeatureVector, long timeoutBudgetMs);

    TabularScoreResult scoreRouteValue(RouteValueFeatureVector routeValueFeatureVector, long timeoutBudgetMs);

    WorkerReadyState readyState();
}

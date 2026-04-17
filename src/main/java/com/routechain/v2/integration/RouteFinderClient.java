package com.routechain.v2.integration;

public interface RouteFinderClient {
    RouteFinderResult refineRoute(RouteFinderFeatureVector featureVector, long timeoutBudgetMs);

    RouteFinderResult generateAlternatives(RouteFinderFeatureVector featureVector, long timeoutBudgetMs);

    WorkerReadyState readyState();
}

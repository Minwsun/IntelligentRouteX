package com.routechain.v2.integration;

public final class NoOpRouteFinderClient implements RouteFinderClient {
    @Override
    public RouteFinderResult refineRoute(RouteFinderFeatureVector featureVector, long timeoutBudgetMs) {
        return RouteFinderResult.notApplied("routefinder-client-disabled");
    }

    @Override
    public RouteFinderResult generateAlternatives(RouteFinderFeatureVector featureVector, long timeoutBudgetMs) {
        return RouteFinderResult.notApplied("routefinder-client-disabled");
    }

    @Override
    public WorkerReadyState readyState() {
        return WorkerReadyState.notReady("routefinder-client-disabled", MlWorkerMetadata.empty());
    }
}

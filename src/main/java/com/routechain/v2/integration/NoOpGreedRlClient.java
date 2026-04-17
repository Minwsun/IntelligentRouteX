package com.routechain.v2.integration;

public final class NoOpGreedRlClient implements GreedRlClient {
    @Override
    public GreedRlBundleResult proposeBundles(GreedRlBundleFeatureVector featureVector, long timeoutBudgetMs) {
        return GreedRlBundleResult.notApplied("greedrl-client-disabled");
    }

    @Override
    public GreedRlSequenceResult proposeSequence(GreedRlSequenceFeatureVector featureVector, long timeoutBudgetMs) {
        return GreedRlSequenceResult.notApplied("greedrl-client-disabled");
    }

    @Override
    public WorkerReadyState readyState() {
        return WorkerReadyState.notReady("greedrl-client-disabled", MlWorkerMetadata.empty());
    }
}

package com.routechain.v2.integration;

public interface GreedRlClient {
    GreedRlBundleResult proposeBundles(GreedRlBundleFeatureVector featureVector, long timeoutBudgetMs);

    GreedRlSequenceResult proposeSequence(GreedRlSequenceFeatureVector featureVector, long timeoutBudgetMs);

    WorkerReadyState readyState();
}

package com.routechain.graph;

/**
 * Namespaces used by the internal feature store.
 * These align with a Feast-compatible split between online, graph, model, and policy features.
 */
public final class GraphFeatureNamespaces {
    public static final String ONLINE_FEATURES = "online_features";
    public static final String GRAPH_FEATURES = "graph_features";
    public static final String MODEL_SCORES = "model_scores";
    public static final String POLICY_STATE = "policy_state";

    private GraphFeatureNamespaces() {}
}

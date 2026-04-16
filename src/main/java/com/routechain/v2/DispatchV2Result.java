package com.routechain.v2;

import com.routechain.v2.cluster.BoundaryExpansion;
import com.routechain.v2.cluster.MicroCluster;

import java.util.List;

public record DispatchV2Result(
        List<DispatchV2PlanCandidate> routePool,
        List<DispatchV2PlanCandidate> selectedRoutes,
        List<OrderSimilarity> pairSimilarities,
        List<MicroCluster> clusters,
        List<BoundaryExpansion> boundaryExpansions,
        long dispatchLatencyMs) {

    public static DispatchV2Result empty() {
        return new DispatchV2Result(List.of(), List.of(), List.of(), List.of(), List.of(), 0L);
    }
}

package com.routechain.v2.cluster;

import java.util.List;

public record BoundaryExpansion(
        String expansionId,
        String sourceClusterId,
        String neighborClusterId,
        List<String> candidateOrderIds,
        double affinityScore) {
}

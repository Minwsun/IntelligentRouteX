package com.routechain.v2.bundle;

import com.routechain.v2.cluster.MicroCluster;

import java.util.List;
import java.util.Map;

public record BundleSeed(
        MicroCluster cluster,
        List<String> workingOrderIds,
        List<String> acceptedBoundaryOrderIds,
        List<String> prioritizedOrderIds,
        Map<String, Double> supportScoreByOrder) {
}

package com.routechain.v2.integration;

import java.util.List;
import java.util.Map;

public record GreedRlBundleFeatureVector(
        String schemaVersion,
        String traceId,
        String clusterId,
        List<String> workingOrderIds,
        List<String> prioritizedOrderIds,
        List<String> acceptedBoundaryOrderIds,
        Map<String, Double> supportScoreByOrder,
        int bundleMaxSize,
        int maxProposals) {
}

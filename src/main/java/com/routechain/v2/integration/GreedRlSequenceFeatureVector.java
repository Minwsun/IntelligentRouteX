package com.routechain.v2.integration;

import java.util.List;

public record GreedRlSequenceFeatureVector(
        String schemaVersion,
        String traceId,
        String clusterId,
        String bundleId,
        List<String> orderIds,
        String anchorOrderId,
        String driverId,
        int maxSequences) {
}

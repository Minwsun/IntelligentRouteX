package com.routechain.v2.integration;

import java.util.List;

public record GreedRlBundleCandidate(
        String family,
        List<String> orderIds,
        List<String> acceptedBoundaryOrderIds,
        boolean boundaryCross,
        List<String> traceReasons) {
}

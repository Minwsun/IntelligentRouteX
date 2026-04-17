package com.routechain.v2.integration;

import java.util.List;

public record RouteFinderFeatureVector(
        String schemaVersion,
        String traceId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        String baselineSource,
        List<String> baselineStopOrder,
        List<String> bundleOrderIds,
        double projectedPickupEtaMinutes,
        double projectedCompletionEtaMinutes,
        double rerankScore,
        double bundleScore,
        double anchorScore,
        double averagePairSupport,
        boolean boundaryCross,
        int maxAlternatives) {
}

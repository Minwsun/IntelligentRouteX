package com.routechain.v2.integration;

import java.util.List;

public record RouteFinderRoute(
        List<String> stopOrder,
        double projectedPickupEtaMinutes,
        double projectedCompletionEtaMinutes,
        double routeScore,
        List<String> traceReasons) {
}

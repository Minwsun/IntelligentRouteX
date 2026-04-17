package com.routechain.v2.integration;

import java.util.List;

public record RouteFinderResult(
        boolean applied,
        List<RouteFinderRoute> routes,
        boolean fallbackUsed,
        String degradeReason,
        MlWorkerMetadata workerMetadata) {

    public static RouteFinderResult applied(List<RouteFinderRoute> routes,
                                            boolean fallbackUsed,
                                            MlWorkerMetadata workerMetadata) {
        return new RouteFinderResult(true, List.copyOf(routes), fallbackUsed, "", workerMetadata);
    }

    public static RouteFinderResult notApplied(String degradeReason, MlWorkerMetadata workerMetadata) {
        return new RouteFinderResult(false, List.of(), true, degradeReason, workerMetadata);
    }

    public static RouteFinderResult notApplied(String degradeReason) {
        return notApplied(degradeReason, MlWorkerMetadata.empty());
    }
}

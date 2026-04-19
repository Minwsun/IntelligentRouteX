package com.routechain.v2.benchmark;

public record DispatchQualityWorkerStatus(
        String workerName,
        boolean enabled,
        String baseUrl,
        boolean reachable,
        boolean ready,
        String readyReason,
        String sourceModel,
        String modelVersion,
        String artifactDigest,
        Boolean loadedFromLocal,
        String expectedFingerprint,
        Boolean fingerprintMatch,
        boolean applied,
        String notAppliedReason) {
}

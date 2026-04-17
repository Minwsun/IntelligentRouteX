package com.routechain.v2.integration;

public record MlWorkerMetadata(
        String sourceModel,
        String modelVersion,
        String artifactDigest,
        long latencyMs) {

    public static MlWorkerMetadata empty() {
        return new MlWorkerMetadata("", "", "", 0L);
    }
}

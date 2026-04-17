package com.routechain.v2.integration;

import java.util.List;

public record GreedRlBundleResult(
        boolean applied,
        boolean fallbackUsed,
        String degradeReason,
        List<GreedRlBundleCandidate> proposals,
        MlWorkerMetadata workerMetadata) {

    public static GreedRlBundleResult applied(List<GreedRlBundleCandidate> proposals, MlWorkerMetadata workerMetadata) {
        return new GreedRlBundleResult(true, false, "", List.copyOf(proposals), workerMetadata);
    }

    public static GreedRlBundleResult notApplied(String degradeReason, MlWorkerMetadata workerMetadata) {
        return new GreedRlBundleResult(false, true, degradeReason, List.of(), workerMetadata);
    }

    public static GreedRlBundleResult notApplied(String degradeReason) {
        return notApplied(degradeReason, MlWorkerMetadata.empty());
    }
}

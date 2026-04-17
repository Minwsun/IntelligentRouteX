package com.routechain.v2.integration;

public record TabularScoreResult(
        boolean applied,
        double value,
        double uncertainty,
        boolean fallbackUsed,
        String degradeReason,
        MlWorkerMetadata workerMetadata) {

    public static TabularScoreResult applied(double value, double uncertainty, boolean fallbackUsed, MlWorkerMetadata workerMetadata) {
        return new TabularScoreResult(true, value, uncertainty, fallbackUsed, "", workerMetadata);
    }

    public static TabularScoreResult notApplied(String degradeReason, MlWorkerMetadata workerMetadata) {
        return new TabularScoreResult(false, 0.0, 0.0, true, degradeReason, workerMetadata);
    }

    public static TabularScoreResult notApplied(String degradeReason) {
        return notApplied(degradeReason, MlWorkerMetadata.empty());
    }
}

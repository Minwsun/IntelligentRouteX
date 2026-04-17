package com.routechain.v2.integration;

import java.util.Map;

public record ForecastResult(
        boolean applied,
        boolean fallbackUsed,
        String degradeReason,
        int horizonMinutes,
        double probability,
        Map<String, Double> quantiles,
        double confidence,
        long sourceAgeMs,
        MlWorkerMetadata workerMetadata) {

    public static ForecastResult applied(int horizonMinutes,
                                         double probability,
                                         Map<String, Double> quantiles,
                                         double confidence,
                                         long sourceAgeMs,
                                         MlWorkerMetadata workerMetadata) {
        return new ForecastResult(
                true,
                false,
                "",
                horizonMinutes,
                probability,
                quantiles == null ? Map.of() : Map.copyOf(quantiles),
                confidence,
                sourceAgeMs,
                workerMetadata);
    }

    public static ForecastResult notApplied(String degradeReason, MlWorkerMetadata workerMetadata) {
        return new ForecastResult(false, true, degradeReason, 0, 0.0, Map.of(), 0.0, 0L, workerMetadata);
    }

    public static ForecastResult notApplied(String degradeReason) {
        return notApplied(degradeReason, MlWorkerMetadata.empty());
    }
}

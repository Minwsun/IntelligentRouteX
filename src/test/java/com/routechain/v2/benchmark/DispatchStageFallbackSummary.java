package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

import java.util.Map;

public record DispatchStageFallbackSummary(
        String schemaVersion,
        int totalStageOutputs,
        int totalFallbacks,
        Map<String, Integer> fallbackCountsByStage,
        Map<String, String> latestFallbackReasonByStage) implements SchemaVersioned {

    public DispatchStageFallbackSummary {
        fallbackCountsByStage = fallbackCountsByStage == null ? Map.of() : Map.copyOf(fallbackCountsByStage);
        latestFallbackReasonByStage = latestFallbackReasonByStage == null ? Map.of() : Map.copyOf(latestFallbackReasonByStage);
    }

    public static DispatchStageFallbackSummary empty() {
        return new DispatchStageFallbackSummary(
                "dispatch-stage-fallback-summary/v1",
                0,
                0,
                Map.of(),
                Map.of());
    }
}

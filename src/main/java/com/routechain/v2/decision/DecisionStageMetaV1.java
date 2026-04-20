package com.routechain.v2.decision;

import com.routechain.v2.SchemaVersioned;

import java.util.Map;

public record DecisionStageMetaV1(
        String schemaVersion,
        long latencyMs,
        double confidence,
        boolean fallbackUsed,
        String fallbackReason,
        boolean validationPassed,
        String appliedSource,
        String requestedEffort,
        String appliedEffort,
        Map<String, Object> tokenUsage,
        int retryCount,
        String rawResponseHash) implements SchemaVersioned {

    public DecisionStageMetaV1 {
        tokenUsage = tokenUsage == null ? Map.of() : Map.copyOf(tokenUsage);
    }

    public static DecisionStageMetaV1 legacy(long latencyMs) {
        return new DecisionStageMetaV1(
                "decision-stage-meta/v1",
                latencyMs,
                1.0,
                false,
                null,
                true,
                "legacy",
                null,
                null,
                Map.of(),
                0,
                null);
    }
}

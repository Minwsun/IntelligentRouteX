package com.routechain.v2;

import java.util.List;

public record LiveStageMetadata(
        String schemaVersion,
        String stageName,
        String sourceName,
        boolean applied,
        boolean fallbackUsed,
        long sourceAgeMs,
        double confidence,
        long latencyMs,
        String degradeReason) implements SchemaVersioned {

    public static List<LiveStageMetadata> emptyList() {
        return List.of();
    }
}

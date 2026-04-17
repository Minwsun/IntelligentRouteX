package com.routechain.v2;

import java.util.List;

public record MlStageMetadata(
        String schemaVersion,
        String stageName,
        String sourceModel,
        String modelVersion,
        String artifactDigest,
        long latencyMs,
        boolean applied,
        boolean fallbackUsed) implements SchemaVersioned {

    public static List<MlStageMetadata> emptyList() {
        return List.of();
    }
}

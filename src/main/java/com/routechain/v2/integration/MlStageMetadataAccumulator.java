package com.routechain.v2.integration;

import com.routechain.v2.MlStageMetadata;

import java.util.Optional;

public final class MlStageMetadataAccumulator {
    private final String stageName;
    private boolean attempted;
    private boolean applied;
    private boolean fallbackUsed;
    private long totalLatencyMs;
    private String sourceModel = "";
    private String modelVersion = "";
    private String artifactDigest = "";

    public MlStageMetadataAccumulator(String stageName) {
        this.stageName = stageName;
    }

    public void accept(TabularScoreResult scoreResult) {
        attempted = true;
        applied = applied || scoreResult.applied();
        fallbackUsed = fallbackUsed || scoreResult.fallbackUsed();
        totalLatencyMs += scoreResult.workerMetadata().latencyMs();
        if (sourceModel.isBlank() && !scoreResult.workerMetadata().sourceModel().isBlank()) {
            sourceModel = scoreResult.workerMetadata().sourceModel();
        }
        if (modelVersion.isBlank() && !scoreResult.workerMetadata().modelVersion().isBlank()) {
            modelVersion = scoreResult.workerMetadata().modelVersion();
        }
        if (artifactDigest.isBlank() && !scoreResult.workerMetadata().artifactDigest().isBlank()) {
            artifactDigest = scoreResult.workerMetadata().artifactDigest();
        }
    }

    public void accept(RouteFinderResult routeFinderResult) {
        attempted = true;
        applied = applied || routeFinderResult.applied();
        fallbackUsed = fallbackUsed || routeFinderResult.fallbackUsed();
        totalLatencyMs += routeFinderResult.workerMetadata().latencyMs();
        if (sourceModel.isBlank() && !routeFinderResult.workerMetadata().sourceModel().isBlank()) {
            sourceModel = routeFinderResult.workerMetadata().sourceModel();
        }
        if (modelVersion.isBlank() && !routeFinderResult.workerMetadata().modelVersion().isBlank()) {
            modelVersion = routeFinderResult.workerMetadata().modelVersion();
        }
        if (artifactDigest.isBlank() && !routeFinderResult.workerMetadata().artifactDigest().isBlank()) {
            artifactDigest = routeFinderResult.workerMetadata().artifactDigest();
        }
    }

    public Optional<MlStageMetadata> build() {
        if (!attempted) {
            return Optional.empty();
        }
        return Optional.of(new MlStageMetadata(
                "ml-stage-metadata/v1",
                stageName,
                sourceModel,
                modelVersion,
                artifactDigest,
                totalLatencyMs,
                applied,
                fallbackUsed));
    }
}

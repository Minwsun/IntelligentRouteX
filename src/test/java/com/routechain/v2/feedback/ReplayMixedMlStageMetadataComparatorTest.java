package com.routechain.v2.feedback;

import com.routechain.v2.MlStageMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayMixedMlStageMetadataComparatorTest {

    @Test
    void comparesMixedStageMetadataByStageAndSourceModelIdentity() {
        List<MlStageMetadata> reference = List.of(
                metadata("route-proposal-pool", "tabular-linear", "v1", "sha256:tabular"),
                metadata("route-proposal-pool", "routefinder-local", "v1", "sha256:routefinder"));
        List<MlStageMetadata> replay = List.of(
                metadata("route-proposal-pool", "tabular-linear", "v1", "sha256:tabular"),
                metadata("route-proposal-pool", "routefinder-local", "v2", "sha256:other"));
        List<String> mismatchReasons = new ArrayList<>();

        ReplayMlMetadataComparator.compare(reference, replay, mismatchReasons);

        assertFalse(mismatchReasons.contains("ml-stage-metadata-mismatch"));
        assertTrue(mismatchReasons.contains("ml-model-version-mismatch"));
        assertTrue(mismatchReasons.contains("ml-artifact-digest-mismatch"));
    }

    private MlStageMetadata metadata(String stageName, String sourceModel, String modelVersion, String artifactDigest) {
        return new MlStageMetadata("ml-stage-metadata/v1", stageName, sourceModel, modelVersion, artifactDigest, 5L, true, false);
    }
}

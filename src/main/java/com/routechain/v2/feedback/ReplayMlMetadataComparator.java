package com.routechain.v2.feedback;

import com.routechain.v2.MlStageMetadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ReplayMlMetadataComparator {
    private ReplayMlMetadataComparator() {
    }

    static void compare(List<MlStageMetadata> reference,
                        List<MlStageMetadata> replay,
                        List<String> mismatchReasons) {
        Map<String, MlStageMetadata> referenceByStage = reference.stream()
                .collect(Collectors.toMap(MlStageMetadata::stageName, metadata -> metadata, (left, right) -> left));
        Map<String, MlStageMetadata> replayByStage = replay.stream()
                .collect(Collectors.toMap(MlStageMetadata::stageName, metadata -> metadata, (left, right) -> left));
        if (!referenceByStage.keySet().equals(replayByStage.keySet())) {
            mismatchReasons.add("ml-stage-metadata-mismatch");
        }
        for (String stageName : referenceByStage.keySet()) {
            MlStageMetadata referenceMetadata = referenceByStage.get(stageName);
            MlStageMetadata replayMetadata = replayByStage.get(stageName);
            if (replayMetadata == null) {
                continue;
            }
            if (!referenceMetadata.modelVersion().equals(replayMetadata.modelVersion())) {
                mismatchReasons.add("ml-model-version-mismatch");
            }
            if (!referenceMetadata.artifactDigest().equals(replayMetadata.artifactDigest())) {
                mismatchReasons.add("ml-artifact-digest-mismatch");
            }
        }
    }
}

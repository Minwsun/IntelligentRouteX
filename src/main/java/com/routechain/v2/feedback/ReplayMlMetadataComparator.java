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
        Map<String, MlStageMetadata> referenceByIdentity = reference.stream()
                .collect(Collectors.toMap(ReplayMlMetadataComparator::identity, metadata -> metadata, (left, right) -> left));
        Map<String, MlStageMetadata> replayByIdentity = replay.stream()
                .collect(Collectors.toMap(ReplayMlMetadataComparator::identity, metadata -> metadata, (left, right) -> left));
        if (!referenceByIdentity.keySet().equals(replayByIdentity.keySet())) {
            mismatchReasons.add("ml-stage-metadata-mismatch");
        }
        for (String identity : referenceByIdentity.keySet()) {
            MlStageMetadata referenceMetadata = referenceByIdentity.get(identity);
            MlStageMetadata replayMetadata = replayByIdentity.get(identity);
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

    private static String identity(MlStageMetadata metadata) {
        return metadata.stageName() + "|" + metadata.sourceModel();
    }
}

package com.routechain.v2.feedback;

import com.routechain.v2.LiveStageMetadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ReplayLiveMetadataComparator {
    private ReplayLiveMetadataComparator() {
    }

    static void compare(List<LiveStageMetadata> reference,
                        List<LiveStageMetadata> replay,
                        List<String> mismatchReasons) {
        Map<String, LiveStageMetadata> referenceByIdentity = reference.stream()
                .collect(Collectors.toMap(ReplayLiveMetadataComparator::identity, metadata -> metadata, (left, right) -> left));
        Map<String, LiveStageMetadata> replayByIdentity = replay.stream()
                .collect(Collectors.toMap(ReplayLiveMetadataComparator::identity, metadata -> metadata, (left, right) -> left));
        if (!referenceByIdentity.keySet().equals(replayByIdentity.keySet())) {
            mismatchReasons.add("live-source-metadata-mismatch");
        }
        for (String identity : referenceByIdentity.keySet()) {
            LiveStageMetadata referenceMetadata = referenceByIdentity.get(identity);
            LiveStageMetadata replayMetadata = replayByIdentity.get(identity);
            if (replayMetadata == null) {
                continue;
            }
            if (referenceMetadata.sourceName().equals("open-meteo")) {
                if (Double.compare(referenceMetadata.confidence(), replayMetadata.confidence()) != 0) {
                    mismatchReasons.add("live-weather-confidence-mismatch");
                }
                if (referenceMetadata.sourceAgeMs() != replayMetadata.sourceAgeMs()) {
                    mismatchReasons.add("live-weather-age-mismatch");
                }
            }
            if (referenceMetadata.sourceName().equals("tomtom-traffic")) {
                if (Double.compare(referenceMetadata.confidence(), replayMetadata.confidence()) != 0) {
                    mismatchReasons.add("live-traffic-confidence-mismatch");
                }
                if (referenceMetadata.sourceAgeMs() != replayMetadata.sourceAgeMs()) {
                    mismatchReasons.add("live-traffic-age-mismatch");
                }
            }
        }
    }

    private static String identity(LiveStageMetadata metadata) {
        return metadata.stageName() + "|" + metadata.sourceName();
    }
}

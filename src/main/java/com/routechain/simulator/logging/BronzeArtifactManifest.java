package com.routechain.simulator.logging;

import java.util.Map;

public record BronzeArtifactManifest(
        String schemaVersion,
        String runId,
        String sliceId,
        Map<String, String> artifactFiles) {
}

package com.routechain.simulator.runtime;

import com.routechain.simulator.logging.BronzeArtifactManifest;

import java.util.Map;

public record RunArtifactBundle(
        BronzeArtifactManifest manifest,
        Map<String, String> traceIdsByFamily) {
}

package com.routechain.infra;

import java.nio.file.Path;

/**
 * Resolves durable artifact roots so benchmarks, runtime sinks, and ops readers stay on the same path.
 */
public final class ArtifactPaths {
    public static final String ARTIFACT_ROOT_PROPERTY = "routechain.artifactRoot";
    public static final String ARTIFACT_ROOT_ENV = "ROUTECHAIN_ARTIFACT_ROOT";
    private static final Path DEFAULT_ROOT = Path.of("build", "routechain-apex");

    private ArtifactPaths() {}

    public static Path root() {
        String configured = System.getProperty(ARTIFACT_ROOT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ARTIFACT_ROOT_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return DEFAULT_ROOT;
        }
        return Path.of(configured.trim());
    }

    public static Path benchmarksRoot() {
        return root().resolve("benchmarks");
    }

    public static Path factsRoot() {
        return root().resolve("facts");
    }

    public static Path eventTapeRoot() {
        return root().resolve("event-tape");
    }

    public static Path controlRoomRoot() {
        return benchmarksRoot().resolve("control-room");
    }
}

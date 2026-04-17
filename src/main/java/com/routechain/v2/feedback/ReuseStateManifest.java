package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.time.Instant;

public record ReuseStateManifest(
        String schemaVersion,
        String reuseStateId,
        String traceId,
        Instant createdAt) implements SchemaVersioned {

    public static ReuseStateManifest fromReuseState(DispatchRuntimeReuseState reuseState) {
        return new ReuseStateManifest(
                "reuse-state-manifest/v1",
                reuseState.reuseStateId(),
                reuseState.traceId(),
                reuseState.createdAt());
    }
}

package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.time.Instant;

public record SnapshotManifest(
        String schemaVersion,
        String snapshotId,
        String traceId,
        Instant createdAt) implements SchemaVersioned {

    public static SnapshotManifest fromSnapshot(DispatchRuntimeSnapshot snapshot) {
        return new SnapshotManifest(
                "snapshot-manifest/v1",
                snapshot.snapshotId(),
                snapshot.traceId(),
                snapshot.createdAt());
    }
}

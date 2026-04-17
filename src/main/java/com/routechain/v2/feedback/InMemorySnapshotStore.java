package com.routechain.v2.feedback;

public final class InMemorySnapshotStore implements SnapshotStore {
    private volatile DispatchRuntimeSnapshot latestSnapshot;
    private final java.util.Map<String, DispatchRuntimeSnapshot> snapshotsByTraceId = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public SnapshotWriteResult save(DispatchRuntimeSnapshot snapshot) {
        latestSnapshot = snapshot;
        snapshotsByTraceId.put(snapshot.traceId(), snapshot);
        return new SnapshotWriteResult(
                "snapshot-write-result/v1",
                snapshot.snapshotId(),
                true,
                SnapshotManifest.fromSnapshot(snapshot),
                snapshot,
                java.util.List.of());
    }

    @Override
    public SnapshotLoadResult loadLatest() {
        if (latestSnapshot == null) {
            return new SnapshotLoadResult(
                    "snapshot-load-result/v1",
                    false,
                    null,
                    null,
                    java.util.List.of("snapshot-not-found"));
        }
        return new SnapshotLoadResult(
                "snapshot-load-result/v1",
                true,
                SnapshotManifest.fromSnapshot(latestSnapshot),
                latestSnapshot,
                java.util.List.of());
    }

    @Override
    public SnapshotLoadResult loadByTraceId(String traceId) {
        DispatchRuntimeSnapshot snapshot = snapshotsByTraceId.get(traceId);
        if (snapshot == null) {
            return new SnapshotLoadResult(
                    "snapshot-load-result/v1",
                    false,
                    null,
                    null,
                    java.util.List.of("snapshot-not-found"));
        }
        return new SnapshotLoadResult(
                "snapshot-load-result/v1",
                true,
                SnapshotManifest.fromSnapshot(snapshot),
                snapshot,
                java.util.List.of());
    }
}

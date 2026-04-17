package com.routechain.v2.feedback;

import java.nio.file.Path;
import java.util.Comparator;

public final class FileSnapshotStore implements SnapshotStore {
    private static final String LATEST_POINTER = "latest.txt";
    private final FeedbackFileSupport fileSupport;

    public FileSnapshotStore(Path baseDirectory, int maxFiles) {
        this.fileSupport = new FeedbackFileSupport(baseDirectory.resolve("snapshots"), maxFiles);
    }

    @Override
    public SnapshotWriteResult save(DispatchRuntimeSnapshot snapshot) {
        String fileName = FeedbackFileSupport.sanitize(snapshot.snapshotId()) + ".json";
        fileSupport.writeJson(fileName, snapshot);
        fileSupport.writePointer(LATEST_POINTER, fileName);
        fileSupport.enforceRetention(LATEST_POINTER);
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
        String latestFileName = fileSupport.readPointer(LATEST_POINTER);
        if (latestFileName == null) {
            return new SnapshotLoadResult(
                    "snapshot-load-result/v1",
                    false,
                    null,
                    null,
                    java.util.List.of("snapshot-not-found"));
        }
        DispatchRuntimeSnapshot snapshot = fileSupport.readJson(latestFileName, DispatchRuntimeSnapshot.class);
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

    @Override
    public SnapshotLoadResult loadByTraceId(String traceId) {
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.exists(fileSupport.directory())
                ? java.nio.file.Files.list(fileSupport.directory())
                : java.util.stream.Stream.empty()) {
            DispatchRuntimeSnapshot snapshot = stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> fileSupport.readJson(path.getFileName().toString(), DispatchRuntimeSnapshot.class))
                    .filter(current -> current != null && traceId.equals(current.traceId()))
                    .max(Comparator.comparing(DispatchRuntimeSnapshot::createdAt))
                    .orElse(null);
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
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to load snapshot by trace id " + traceId, exception);
        }
    }
}

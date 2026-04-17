package com.routechain.v2.feedback;

import java.nio.file.Path;
import java.util.Comparator;

public final class FileReuseStateStore implements ReuseStateStore {
    private static final String LATEST_POINTER = "latest.txt";
    private final FeedbackFileSupport fileSupport;

    public FileReuseStateStore(Path baseDirectory, int maxFiles) {
        this.fileSupport = new FeedbackFileSupport(baseDirectory.resolve("reuse-states"), maxFiles);
    }

    @Override
    public ReuseStateWriteResult save(DispatchRuntimeReuseState reuseState) {
        String fileName = FeedbackFileSupport.sanitize(reuseState.reuseStateId()) + ".json";
        fileSupport.writeJson(fileName, reuseState);
        fileSupport.writePointer(LATEST_POINTER, fileName);
        fileSupport.enforceRetention(LATEST_POINTER);
        return new ReuseStateWriteResult(
                "reuse-state-write-result/v1",
                reuseState.reuseStateId(),
                true,
                ReuseStateManifest.fromReuseState(reuseState),
                reuseState,
                java.util.List.of());
    }

    @Override
    public ReuseStateLoadResult loadLatest() {
        String latestFileName = fileSupport.readPointer(LATEST_POINTER);
        if (latestFileName == null) {
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    false,
                    null,
                    null,
                    java.util.List.of("reuse-state-not-found"));
        }
        DispatchRuntimeReuseState reuseState = fileSupport.readJson(latestFileName, DispatchRuntimeReuseState.class);
        if (reuseState == null) {
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    false,
                    null,
                    null,
                    java.util.List.of("reuse-state-not-found"));
        }
        return new ReuseStateLoadResult(
                "reuse-state-load-result/v1",
                true,
                ReuseStateManifest.fromReuseState(reuseState),
                reuseState,
                java.util.List.of());
    }

    @Override
    public ReuseStateLoadResult loadByTraceId(String traceId) {
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.exists(fileSupport.directory())
                ? java.nio.file.Files.list(fileSupport.directory())
                : java.util.stream.Stream.empty()) {
            DispatchRuntimeReuseState reuseState = stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> fileSupport.readJson(path.getFileName().toString(), DispatchRuntimeReuseState.class))
                    .filter(current -> current != null && traceId.equals(current.traceId()))
                    .max(Comparator.comparing(DispatchRuntimeReuseState::createdAt))
                    .orElse(null);
            if (reuseState == null) {
                return new ReuseStateLoadResult(
                        "reuse-state-load-result/v1",
                        false,
                        null,
                        null,
                        java.util.List.of("reuse-state-not-found"));
            }
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    true,
                    ReuseStateManifest.fromReuseState(reuseState),
                    reuseState,
                    java.util.List.of());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to load reuse state by trace id " + traceId, exception);
        }
    }
}

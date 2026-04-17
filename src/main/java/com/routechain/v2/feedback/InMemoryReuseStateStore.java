package com.routechain.v2.feedback;

public final class InMemoryReuseStateStore implements ReuseStateStore {
    private volatile DispatchRuntimeReuseState latestReuseState;
    private final java.util.Map<String, DispatchRuntimeReuseState> reuseStatesByTraceId = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public ReuseStateWriteResult save(DispatchRuntimeReuseState reuseState) {
        latestReuseState = reuseState;
        reuseStatesByTraceId.put(reuseState.traceId(), reuseState);
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
        if (latestReuseState == null) {
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
                ReuseStateManifest.fromReuseState(latestReuseState),
                latestReuseState,
                java.util.List.of());
    }

    @Override
    public ReuseStateLoadResult loadByTraceId(String traceId) {
        DispatchRuntimeReuseState reuseState = reuseStatesByTraceId.get(traceId);
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
}

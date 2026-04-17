package com.routechain.v2.feedback;

public interface SnapshotStore {
    SnapshotWriteResult save(DispatchRuntimeSnapshot snapshot);

    SnapshotLoadResult loadLatest();

    SnapshotLoadResult loadByTraceId(String traceId);
}

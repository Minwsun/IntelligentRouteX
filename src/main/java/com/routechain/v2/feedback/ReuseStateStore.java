package com.routechain.v2.feedback;

public interface ReuseStateStore {
    ReuseStateWriteResult save(DispatchRuntimeReuseState reuseState);

    ReuseStateLoadResult loadLatest();

    ReuseStateLoadResult loadByTraceId(String traceId);
}

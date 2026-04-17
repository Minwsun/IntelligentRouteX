package com.routechain.v2.feedback;

public interface ReplayStore {
    ReplayRequestRecord save(ReplayRequestRecord record);

    ReplayRequestRecord latest();

    ReplayRequestRecord findByTraceId(String traceId);
}

package com.routechain.v2.feedback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryReplayStore implements ReplayStore {
    private final Map<String, ReplayRequestRecord> recordsByTraceId = new ConcurrentHashMap<>();
    private volatile ReplayRequestRecord latest;

    @Override
    public ReplayRequestRecord save(ReplayRequestRecord record) {
        latest = record;
        recordsByTraceId.put(record.traceId(), record);
        return record;
    }

    @Override
    public ReplayRequestRecord latest() {
        return latest;
    }

    @Override
    public ReplayRequestRecord findByTraceId(String traceId) {
        return recordsByTraceId.get(traceId);
    }
}

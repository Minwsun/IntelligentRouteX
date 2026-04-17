package com.routechain.v2.feedback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDecisionLogWriter implements DecisionLogWriter {
    private final Map<String, DecisionLogRecord> recordsByTraceId = new ConcurrentHashMap<>();
    private volatile DecisionLogRecord latest;

    @Override
    public DecisionLogRecord write(DecisionLogRecord record) {
        latest = record;
        recordsByTraceId.put(record.traceId(), record);
        return record;
    }

    @Override
    public DecisionLogRecord latest() {
        return latest;
    }

    @Override
    public DecisionLogRecord findByTraceId(String traceId) {
        return recordsByTraceId.get(traceId);
    }
}

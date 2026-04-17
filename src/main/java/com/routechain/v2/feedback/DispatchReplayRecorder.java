package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DispatchReplayRecorder {
    private final RouteChainDispatchV2Properties properties;
    private final Map<String, ReplayRequestRecord> recordsByTraceId = new ConcurrentHashMap<>();
    private volatile ReplayRequestRecord latest;

    public DispatchReplayRecorder(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public ReplayRequestRecord record(DispatchV2Request request) {
        if (!properties.getFeedback().isReplayEnabled()) {
            return null;
        }
        ReplayRequestRecord record = new ReplayRequestRecord(
                "replay-request-record/v1",
                request.traceId(),
                request);
        latest = record;
        recordsByTraceId.put(record.traceId(), record);
        return record;
    }

    public ReplayRequestRecord latest() {
        return latest;
    }
}

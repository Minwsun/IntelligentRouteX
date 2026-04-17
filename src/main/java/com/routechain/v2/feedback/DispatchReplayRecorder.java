package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;

public final class DispatchReplayRecorder {
    private final RouteChainDispatchV2Properties properties;
    private final ReplayStore replayStore;

    public DispatchReplayRecorder(RouteChainDispatchV2Properties properties, ReplayStore replayStore) {
        this.properties = properties;
        this.replayStore = replayStore;
    }

    public ReplayRequestRecord record(DispatchV2Request request) {
        if (!properties.getFeedback().isReplayEnabled()) {
            return null;
        }
        ReplayRequestRecord record = new ReplayRequestRecord(
                "replay-request-record/v1",
                request.traceId(),
                request);
        return replayStore.save(record);
    }

    public ReplayRequestRecord latest() {
        return replayStore.latest();
    }

    public ReplayRequestRecord findByTraceId(String traceId) {
        return replayStore.findByTraceId(traceId);
    }
}

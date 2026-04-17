package com.routechain.v2.feedback;

public final class DispatchReplayLoader {
    private final DispatchReplayRecorder dispatchReplayRecorder;
    private final DecisionLogService decisionLogService;
    private final SnapshotService snapshotService;

    public DispatchReplayLoader(DispatchReplayRecorder dispatchReplayRecorder,
                                DecisionLogService decisionLogService,
                                SnapshotService snapshotService) {
        this.dispatchReplayRecorder = dispatchReplayRecorder;
        this.decisionLogService = decisionLogService;
        this.snapshotService = snapshotService;
    }

    public ReplayRequestRecord loadLatestRequestRecord() {
        return dispatchReplayRecorder.latest();
    }

    public ReplayRequestRecord loadRequestRecord(String traceId) {
        return dispatchReplayRecorder.findByTraceId(traceId);
    }

    public DecisionLogRecord loadLatestDecisionLog() {
        return decisionLogService.latest();
    }

    public DecisionLogRecord loadDecisionLog(String traceId) {
        return decisionLogService.findByTraceId(traceId);
    }

    public SnapshotLoadResult loadLatestSnapshot() {
        return snapshotService.loadLatest();
    }

    public SnapshotLoadResult loadSnapshot(String traceId) {
        return snapshotService.loadByTraceId(traceId);
    }
}

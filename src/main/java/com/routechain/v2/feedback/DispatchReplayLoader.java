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

    public DecisionLogRecord loadLatestDecisionLog() {
        return decisionLogService.latest();
    }

    public SnapshotLoadResult loadLatestSnapshot() {
        return snapshotService.loadLatest();
    }
}

package com.routechain.v2.feedback;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.HotStartState;

public final class PostDispatchHardeningService {
    private final DispatchReplayRecorder dispatchReplayRecorder;
    private final DecisionLogService decisionLogService;
    private final SnapshotService snapshotService;
    private final HotStartManager hotStartManager;

    public PostDispatchHardeningService(DispatchReplayRecorder dispatchReplayRecorder,
                                        DecisionLogService decisionLogService,
                                        SnapshotService snapshotService,
                                        HotStartManager hotStartManager) {
        this.dispatchReplayRecorder = dispatchReplayRecorder;
        this.decisionLogService = decisionLogService;
        this.snapshotService = snapshotService;
        this.hotStartManager = hotStartManager;
    }

    public DispatchV2Result apply(DispatchV2Request request, DispatchV2Result pipelineResult) {
        dispatchReplayRecorder.record(request);
        decisionLogService.write(request, pipelineResult);
        SnapshotWriteResult snapshotWriteResult = snapshotService.save(request, pipelineResult);
        HotStartState hotStartState = hotStartManager.update(snapshotWriteResult.snapshot());
        return pipelineResult.withHotStartState(hotStartState);
    }
}

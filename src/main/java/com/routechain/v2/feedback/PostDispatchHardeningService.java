package com.routechain.v2.feedback;

import com.routechain.v2.DispatchPipelineExecution;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.HotStartState;

public final class PostDispatchHardeningService {
    private final DispatchReplayRecorder dispatchReplayRecorder;
    private final DecisionLogService decisionLogService;
    private final SnapshotService snapshotService;
    private final ReuseStateService reuseStateService;
    private final HotStartManager hotStartManager;

    public PostDispatchHardeningService(DispatchReplayRecorder dispatchReplayRecorder,
                                        DecisionLogService decisionLogService,
                                        SnapshotService snapshotService,
                                        ReuseStateService reuseStateService,
                                        HotStartManager hotStartManager) {
        this.dispatchReplayRecorder = dispatchReplayRecorder;
        this.decisionLogService = decisionLogService;
        this.snapshotService = snapshotService;
        this.reuseStateService = reuseStateService;
        this.hotStartManager = hotStartManager;
    }

    public HotStartReusePlan planHotStartReuse(com.routechain.v2.EtaContext etaContext) {
        return hotStartManager.plan(etaContext);
    }

    public DispatchV2Result apply(DispatchV2Request request,
                                  DispatchPipelineExecution execution,
                                  HotStartReusePlan reusePlan,
                                  HotStartAppliedReuse appliedReuse) {
        DispatchV2Result pipelineResult = execution.result();
        dispatchReplayRecorder.record(request);
        decisionLogService.write(request, pipelineResult);
        snapshotService.save(request, pipelineResult);
        ReuseStateWriteResult reuseStateWriteResult = reuseStateService.save(request, execution);
        HotStartState hotStartState = hotStartManager.update(reuseStateWriteResult.reuseState(), reusePlan, appliedReuse);
        return pipelineResult.withHotStartState(hotStartState);
    }
}

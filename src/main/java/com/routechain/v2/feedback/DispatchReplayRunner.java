package com.routechain.v2.feedback;

import com.routechain.v2.DispatchV2Core;
import com.routechain.v2.DispatchV2Result;

import java.util.ArrayList;
import java.util.List;

public final class DispatchReplayRunner {
    private final DispatchV2Core dispatchV2Core;
    private final DispatchReplayLoader dispatchReplayLoader;
    private final DispatchReplayComparator dispatchReplayComparator;

    public DispatchReplayRunner(DispatchV2Core dispatchV2Core,
                                DispatchReplayLoader dispatchReplayLoader,
                                DispatchReplayComparator dispatchReplayComparator) {
        this.dispatchV2Core = dispatchV2Core;
        this.dispatchReplayLoader = dispatchReplayLoader;
        this.dispatchReplayComparator = dispatchReplayComparator;
    }

    public ReplayRunResult replayLatest() {
        ReplayRequestRecord requestRecord = dispatchReplayLoader.loadLatestRequestRecord();
        if (requestRecord == null) {
            return new ReplayRunResult(
                    "replay-run-result/v1",
                    null,
                    null,
                    null,
                    new ReplayComparisonResult("replay-comparison-result/v1", false, List.of("replay-reference-missing")),
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    0,
                    List.of("replay-request-missing"));
        }

        DecisionLogRecord decisionLogRecord = dispatchReplayLoader.loadLatestDecisionLog();
        SnapshotLoadResult snapshotLoadResult = dispatchReplayLoader.loadLatestSnapshot();
        DispatchV2Result replayResult = dispatchV2Core.dispatch(requestRecord.request());
        ReplayComparisonResult comparisonResult = dispatchReplayComparator.compare(
                decisionLogRecord,
                snapshotLoadResult.snapshot(),
                replayResult);

        List<String> degradeReasons = new ArrayList<>();
        degradeReasons.addAll(snapshotLoadResult.degradeReasons());
        degradeReasons.addAll(comparisonResult.mismatchReasons());

        return new ReplayRunResult(
                "replay-run-result/v1",
                requestRecord,
                decisionLogRecord,
                snapshotLoadResult.manifest(),
                comparisonResult,
                replayResult.decisionStages(),
                replayResult.globalSelectionResult().selectedProposals().stream()
                        .map(selectedProposal -> selectedProposal.proposalId())
                        .toList(),
                replayResult.assignments().stream()
                        .map(assignment -> assignment.assignmentId())
                        .toList(),
                replayResult.globalSelectionResult().selectedCount(),
                replayResult.dispatchExecutionSummary().executedAssignmentCount(),
                degradeReasons.stream().distinct().toList());
    }
}

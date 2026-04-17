package com.routechain.v2.feedback;

import com.routechain.v2.DispatchV2Result;

import java.util.ArrayList;
import java.util.List;

public final class DispatchReplayComparator {

    public ReplayComparisonResult compare(DecisionLogRecord referenceDecisionLog,
                                          DispatchRuntimeSnapshot referenceSnapshot,
                                          DispatchV2Result replayResult) {
        List<String> mismatchReasons = new ArrayList<>();

        List<String> expectedDecisionStages = referenceDecisionLog != null
                ? referenceDecisionLog.decisionStages()
                : referenceSnapshot != null ? referenceSnapshot.decisionStages() : null;
        List<String> expectedSelectedProposalIds = referenceDecisionLog != null
                ? referenceDecisionLog.selectedProposalIds()
                : referenceSnapshot != null ? referenceSnapshot.selectedProposalIds() : null;
        List<String> expectedExecutedAssignmentIds = referenceDecisionLog != null
                ? referenceDecisionLog.executedAssignmentIds()
                : referenceSnapshot != null ? referenceSnapshot.executedAssignmentIds() : null;
        Integer expectedSelectedCount = referenceDecisionLog != null
                ? referenceDecisionLog.globalSelectorSummary().selectedCount()
                : expectedSelectedProposalIds != null ? expectedSelectedProposalIds.size() : null;
        Integer expectedExecutedAssignmentCount = referenceDecisionLog != null
                ? referenceDecisionLog.dispatchExecutionSummary().executedAssignmentCount()
                : expectedExecutedAssignmentIds != null ? expectedExecutedAssignmentIds.size() : null;

        if (expectedDecisionStages == null && expectedSelectedProposalIds == null && expectedExecutedAssignmentIds == null) {
            mismatchReasons.add("replay-reference-missing");
        }
        if (expectedDecisionStages != null && !expectedDecisionStages.equals(replayResult.decisionStages())) {
            mismatchReasons.add("decision-stages-mismatch");
        }
        List<String> replaySelectedProposalIds = replayResult.globalSelectionResult().selectedProposals().stream()
                .map(selectedProposal -> selectedProposal.proposalId())
                .toList();
        if (expectedSelectedProposalIds != null && !expectedSelectedProposalIds.equals(replaySelectedProposalIds)) {
            mismatchReasons.add("selected-proposal-ids-mismatch");
        }
        List<String> replayExecutedAssignmentIds = replayResult.assignments().stream()
                .map(assignment -> assignment.assignmentId())
                .toList();
        if (expectedExecutedAssignmentIds != null && !expectedExecutedAssignmentIds.equals(replayExecutedAssignmentIds)) {
            mismatchReasons.add("executed-assignment-ids-mismatch");
        }
        if (expectedSelectedCount != null && expectedSelectedCount != replayResult.globalSelectionResult().selectedCount()) {
            mismatchReasons.add("selected-count-mismatch");
        }
        if (expectedExecutedAssignmentCount != null
                && expectedExecutedAssignmentCount != replayResult.dispatchExecutionSummary().executedAssignmentCount()) {
            mismatchReasons.add("executed-assignment-count-mismatch");
        }

        return new ReplayComparisonResult(
                "replay-comparison-result/v1",
                mismatchReasons.isEmpty(),
                List.copyOf(mismatchReasons));
    }
}

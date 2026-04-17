package com.routechain.v2.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DispatchAssignmentBuilder {

    public DispatchAssignmentBuildResult build(ResolvedSelectedProposal resolvedSelectedProposal,
                                               com.routechain.v2.route.DispatchCandidateContext context) {
        List<String> reasons = new ArrayList<>();
        reasons.add("selected-by-global-selector");
        reasons.add("executor-assignment-materialized");
        reasons.addAll(resolvedSelectedProposal.selectedProposal().reasons());
        String assignmentId = resolvedSelectedProposal.selectedProposal().proposalId()
                + "|" + resolvedSelectedProposal.selectorCandidate().driverId()
                + "|" + resolvedSelectedProposal.selectedProposal().selectionRank();
        DispatchAssignment assignment = new DispatchAssignment(
                "dispatch-assignment/v2",
                assignmentId,
                resolvedSelectedProposal.selectedProposal().proposalId(),
                resolvedSelectedProposal.selectorCandidate().bundleId(),
                resolvedSelectedProposal.selectorCandidate().anchorOrderId(),
                resolvedSelectedProposal.selectorCandidate().driverId(),
                resolvedSelectedProposal.selectorCandidate().orderIds(),
                resolvedSelectedProposal.routeProposal().stopOrder(),
                ExecutionActionType.ASSIGN_DRIVER,
                resolvedSelectedProposal.routeProposal().source(),
                resolvedSelectedProposal.selectedProposal().selectionRank(),
                resolvedSelectedProposal.selectedProposal().selectionScore(),
                resolvedSelectedProposal.selectorCandidate().robustUtility(),
                resolvedSelectedProposal.routeProposal().projectedPickupEtaMinutes(),
                resolvedSelectedProposal.routeProposal().projectedCompletionEtaMinutes(),
                resolvedSelectedProposal.selectorCandidate().routeValue(),
                resolvedSelectedProposal.selectorCandidate().clusterId(),
                resolvedSelectedProposal.selectorCandidate().boundaryCross(),
                context.readyWindowStart(resolvedSelectedProposal.selectorCandidate().bundleId()),
                context.readyWindowEnd(resolvedSelectedProposal.selectorCandidate().bundleId()),
                List.copyOf(reasons),
                java.util.stream.Stream.concat(
                                resolvedSelectedProposal.selectorCandidate().degradeReasons().stream(),
                                resolvedSelectedProposal.routeProposal().degradeReasons().stream())
                        .distinct()
                        .toList());
        return new DispatchAssignmentBuildResult(
                Optional.of(assignment),
                new DispatchExecutionTrace(
                        List.of(),
                        List.of(),
                        List.of(assignment.proposalId() + ":assignment-built"),
                        List.of(),
                        List.of(assignment.assignmentId()),
                        "assignment-built"),
                List.of());
    }
}

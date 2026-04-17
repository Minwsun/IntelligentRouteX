package com.routechain.v2.executor;

import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DispatchAssignmentBuilder {

    public DispatchAssignmentBuildResult build(SelectedProposal selectedProposal,
                                               SelectorCandidate selectorCandidate,
                                               RouteProposal routeProposal,
                                               DispatchCandidateContext context) {
        if (selectorCandidate == null || routeProposal == null) {
            return new DispatchAssignmentBuildResult(
                    Optional.empty(),
                    new DispatchExecutionTrace(
                            List.of(selectedProposal.proposalId()),
                            List.of(),
                            List.of(),
                            "missing-upstream-context"),
                    List.of("executor-missing-upstream-context"));
        }
        BundleCandidate bundleCandidate = context.bundle(selectorCandidate.bundleId());
        if (bundleCandidate == null) {
            return new DispatchAssignmentBuildResult(
                    Optional.empty(),
                    new DispatchExecutionTrace(
                            List.of(selectedProposal.proposalId()),
                            List.of(),
                            List.of(),
                            "missing-upstream-context"),
                    List.of("executor-missing-upstream-context"));
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("selected-by-global-selector");
        reasons.add("executor-assignment-materialized");
        reasons.addAll(selectedProposal.reasons());
        DispatchAssignment assignment = new DispatchAssignment(
                "dispatch-assignment/v1",
                selectedProposal.proposalId(),
                selectorCandidate.bundleId(),
                selectorCandidate.anchorOrderId(),
                selectorCandidate.driverId(),
                selectorCandidate.orderIds(),
                routeProposal.stopOrder(),
                ExecutionActionType.ASSIGN_DRIVER,
                selectedProposal.selectionRank(),
                selectedProposal.selectionScore(),
                selectorCandidate.robustUtility(),
                selectorCandidate.routeValue(),
                selectorCandidate.clusterId(),
                selectorCandidate.boundaryCross(),
                context.readyWindowStart(selectorCandidate.bundleId()),
                context.readyWindowEnd(selectorCandidate.bundleId()),
                List.copyOf(reasons),
                java.util.stream.Stream.concat(selectorCandidate.degradeReasons().stream(), routeProposal.degradeReasons().stream())
                        .distinct()
                        .toList());
        return new DispatchAssignmentBuildResult(
                Optional.of(assignment),
                new DispatchExecutionTrace(
                        List.of(),
                        List.of(),
                        List.of(assignment.proposalId() + ":assignment-built"),
                        "not-selected-yet"),
                List.of());
    }
}

package com.routechain.v2.executor;

import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.selector.GlobalSelectionResult;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class DispatchExecutor {
    private final DispatchAssignmentBuilder dispatchAssignmentBuilder;

    public DispatchExecutor(DispatchAssignmentBuilder dispatchAssignmentBuilder) {
        this.dispatchAssignmentBuilder = dispatchAssignmentBuilder;
    }

    public DispatchExecutorResult execute(GlobalSelectionResult globalSelectionResult,
                                          List<SelectorCandidate> selectorCandidates,
                                          List<RouteProposal> routeProposals,
                                          DispatchCandidateContext context) {
        Map<String, SelectorCandidate> selectorCandidateByProposalId = selectorCandidates.stream()
                .collect(java.util.stream.Collectors.toMap(SelectorCandidate::proposalId, candidate -> candidate, (left, right) -> left));
        Map<String, RouteProposal> routeProposalById = routeProposals.stream()
                .collect(java.util.stream.Collectors.toMap(RouteProposal::proposalId, proposal -> proposal, (left, right) -> left));

        List<SelectedProposal> orderedSelectedProposals = globalSelectionResult.selectedProposals().stream()
                .sorted(Comparator.comparingInt(SelectedProposal::selectionRank)
                        .thenComparing(Comparator.comparingDouble(SelectedProposal::selectionScore).reversed())
                        .thenComparing(SelectedProposal::proposalId))
                .toList();

        List<DispatchAssignment> assignments = new ArrayList<>();
        List<String> missingContextProposalIds = new ArrayList<>();
        List<String> ordering = new ArrayList<>();
        List<String> assignmentBuildReasons = new ArrayList<>();
        List<String> degradeReasons = new ArrayList<>();

        for (SelectedProposal selectedProposal : orderedSelectedProposals) {
            ordering.add(selectedProposal.proposalId());
            DispatchAssignmentBuildResult buildResult = dispatchAssignmentBuilder.build(
                    selectedProposal,
                    selectorCandidateByProposalId.get(selectedProposal.proposalId()),
                    routeProposalById.get(selectedProposal.proposalId()),
                    context);
            buildResult.assignment().ifPresent(assignments::add);
            missingContextProposalIds.addAll(buildResult.trace().missingContextProposalIds());
            assignmentBuildReasons.addAll(buildResult.trace().assignmentBuildReasons());
            degradeReasons.addAll(buildResult.degradeReasons());
        }

        String selectedRouteId = assignments.isEmpty() ? null : assignments.getFirst().proposalId();
        return new DispatchExecutorResult(
                List.copyOf(assignments),
                selectedRouteId,
                new DispatchExecutionTrace(
                        List.copyOf(missingContextProposalIds),
                        List.copyOf(ordering),
                        List.copyOf(assignmentBuildReasons),
                        selectedRouteId == null ? "no-executed-assignment" : "selected-route-id-from-first-executed-assignment"),
                degradeReasons.stream().distinct().toList());
    }
}

package com.routechain.v2.executor;

import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.selector.GlobalSelectionResult;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class DispatchExecutor {
    private final SelectedProposalResolver selectedProposalResolver;
    private final ExecutionConflictValidator executionConflictValidator;
    private final DispatchAssignmentBuilder dispatchAssignmentBuilder;

    public DispatchExecutor(SelectedProposalResolver selectedProposalResolver,
                            ExecutionConflictValidator executionConflictValidator,
                            DispatchAssignmentBuilder dispatchAssignmentBuilder) {
        this.selectedProposalResolver = selectedProposalResolver;
        this.executionConflictValidator = executionConflictValidator;
        this.dispatchAssignmentBuilder = dispatchAssignmentBuilder;
    }

    public DispatchExecutorResult execute(GlobalSelectionResult globalSelectionResult,
                                          List<SelectorCandidate> selectorCandidates,
                                          List<RouteProposal> routeProposals,
                                          DispatchRouteCandidateStage routeCandidateStage,
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

        List<String> missingContextProposalIds = new ArrayList<>();
        List<String> ordering = new ArrayList<>();
        List<ResolvedSelectedProposal> resolvedSelectedProposals = new ArrayList<>();
        List<String> degradeReasons = new ArrayList<>();

        for (SelectedProposal selectedProposal : orderedSelectedProposals) {
            ordering.add(selectedProposal.proposalId());
            SelectedProposalResolveResult resolveResult = selectedProposalResolver.resolve(
                    selectedProposal,
                    selectorCandidateByProposalId,
                    routeProposalById,
                    routeCandidateStage,
                    context);
            resolveResult.resolvedProposal().ifPresent(resolvedSelectedProposals::add);
            missingContextProposalIds.addAll(resolveResult.trace().missingContextProposalIds());
            degradeReasons.addAll(resolveResult.degradeReasons());
        }

        ExecutionConflictValidationResult validationResult = executionConflictValidator.validate(resolvedSelectedProposals);
        List<String> assignmentBuildReasons = new ArrayList<>();
        List<String> emittedAssignmentIds = new ArrayList<>();
        List<DispatchAssignment> assignments = new ArrayList<>();
        for (ResolvedSelectedProposal resolvedSelectedProposal : validationResult.acceptedProposals()) {
            DispatchAssignmentBuildResult buildResult = dispatchAssignmentBuilder.build(resolvedSelectedProposal, context);
            buildResult.assignment().ifPresent(assignments::add);
            assignmentBuildReasons.addAll(buildResult.trace().assignmentBuildReasons());
            emittedAssignmentIds.addAll(buildResult.trace().emittedAssignmentIds());
            degradeReasons.addAll(buildResult.degradeReasons());
        }
        degradeReasons.addAll(validationResult.degradeReasons());

        return new DispatchExecutorResult(
                List.copyOf(assignments),
                globalSelectionResult.selectedCount(),
                resolvedSelectedProposals.size(),
                validationResult.trace().conflictRejectedProposalIds().size(),
                new DispatchExecutionTrace(
                        List.copyOf(missingContextProposalIds),
                        List.copyOf(ordering),
                        List.copyOf(assignmentBuildReasons),
                        validationResult.trace().conflictRejectedProposalIds(),
                        List.copyOf(emittedAssignmentIds),
                        assignments.isEmpty() ? "no-executed-assignment" : "executed-assignments-emitted"),
                degradeReasons.stream().distinct().toList());
    }
}

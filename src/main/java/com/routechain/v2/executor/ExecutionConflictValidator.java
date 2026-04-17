package com.routechain.v2.executor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExecutionConflictValidator {

    public ExecutionConflictValidationResult validate(List<ResolvedSelectedProposal> resolvedSelectedProposals) {
        Set<String> seenDrivers = new HashSet<>();
        Set<String> seenOrders = new HashSet<>();
        List<ResolvedSelectedProposal> accepted = new ArrayList<>();
        List<String> rejectedProposalIds = new ArrayList<>();
        List<String> degradeReasons = new ArrayList<>();
        for (ResolvedSelectedProposal resolved : resolvedSelectedProposals) {
            boolean driverConflict = !seenDrivers.add(resolved.selectorCandidate().driverId());
            boolean orderConflict = resolved.selectorCandidate().orderIds().stream().anyMatch(orderId -> !seenOrders.add(orderId));
            if (driverConflict || orderConflict) {
                rejectedProposalIds.add(resolved.selectedProposal().proposalId());
                degradeReasons.add("executor-conflict-validation-failed");
                continue;
            }
            accepted.add(resolved);
        }
        return new ExecutionConflictValidationResult(
                List.copyOf(accepted),
                new DispatchExecutionTrace(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.copyOf(rejectedProposalIds),
                        List.of(),
                        rejectedProposalIds.isEmpty() ? "no-execution-conflicts" : "execution-conflicts-skipped"),
                degradeReasons.stream().distinct().toList());
    }
}

package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;

import java.util.List;
import java.util.Map;

public final class ExecutorCertificationSupport {
    private ExecutorCertificationSupport() {
    }

    public static ExecutorConflictEvidence defensiveSkipEvidence() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        DispatchSelectorStage selectorStage = RouteTestFixtures.selectorStage(properties);
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                RouteTestFixtures.request().availableDrivers(),
                pairClusterStage,
                bundleStage);
        SelectedProposalResolver resolver = new SelectedProposalResolver();
        ExecutionConflictValidator validator = new ExecutionConflictValidator();
        SelectedProposal firstSelected = selectorStage.globalSelectionResult().selectedProposals().getFirst();
        SelectorCandidate firstCandidate = selectorStage.selectorCandidates().stream()
                .filter(candidate -> candidate.proposalId().equals(firstSelected.proposalId()))
                .findFirst()
                .orElseThrow();
        RouteProposal firstRoute = routeProposalStage.routeProposals().stream()
                .filter(proposal -> proposal.proposalId().equals(firstSelected.proposalId()))
                .findFirst()
                .orElseThrow();
        ResolvedSelectedProposal firstResolved = resolver.resolve(
                        firstSelected,
                        Map.of(firstCandidate.proposalId(), firstCandidate),
                        Map.of(firstRoute.proposalId(), firstRoute),
                        routeCandidateStage,
                        context)
                .resolvedProposal()
                .orElseThrow();
        ResolvedSelectedProposal conflictingResolved = new ResolvedSelectedProposal(
                new SelectedProposal(
                        firstSelected.schemaVersion(),
                        firstSelected.proposalId() + "-later",
                        firstSelected.selectionRank() + 1,
                        firstSelected.selectionScore() - 0.01,
                        firstSelected.reasons()),
                new SelectorCandidate(
                        firstCandidate.schemaVersion(),
                        firstCandidate.proposalId() + "-later",
                        firstCandidate.bundleId(),
                        firstCandidate.anchorOrderId(),
                        firstCandidate.driverId(),
                        firstCandidate.orderIds(),
                        firstCandidate.robustUtility(),
                        firstCandidate.routeValue(),
                        firstCandidate.source(),
                        firstCandidate.clusterId(),
                        firstCandidate.boundaryCross(),
                        firstCandidate.selectionScore() - 0.01,
                        firstCandidate.feasible(),
                        firstCandidate.reasons(),
                        firstCandidate.degradeReasons()),
                firstResolved.routeProposal(),
                firstResolved.bundleCandidate(),
                firstResolved.pickupAnchor(),
                firstResolved.driverCandidate());

        ExecutionConflictValidationResult result = validator.validate(List.of(firstResolved, conflictingResolved));
        boolean conflictFree = result.acceptedProposals().size() == 1
                && result.acceptedProposals().getFirst().selectedProposal().proposalId().equals(firstResolved.selectedProposal().proposalId());
        return new ExecutorConflictEvidence(
                conflictFree,
                result.trace().conflictRejectedProposalIds(),
                result.degradeReasons());
    }

    public record ExecutorConflictEvidence(
            boolean conflictFreeAssignments,
            List<String> rejectedProposalIds,
            List<String> degradeReasons) {
    }
}

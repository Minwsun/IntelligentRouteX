package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionConflictValidatorTest {

    @Test
    void detectsDriverAndOrderOverlapAndKeepsEarlierProposalDeterministically() {
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
                new com.routechain.v2.selector.SelectedProposal(
                        firstSelected.schemaVersion(),
                        firstSelected.proposalId() + "-later",
                        firstSelected.selectionRank() + 1,
                        firstSelected.selectionScore() - 0.01,
                        firstSelected.reasons()),
                new com.routechain.v2.selector.SelectorCandidate(
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

        assertEquals(1, result.acceptedProposals().size());
        assertEquals(firstResolved.selectedProposal().proposalId(), result.acceptedProposals().getFirst().selectedProposal().proposalId());
        assertTrue(result.degradeReasons().contains("executor-conflict-validation-failed"));
    }

    @Test
    void rejectedProposalDoesNotReserveOrdersOrDriverForLaterIndependentProposal() {
        ResolvedSelectedProposal accepted = resolvedProposal(
                "proposal-1",
                1,
                0.90,
                "driver-1",
                List.of("order-a"));
        ResolvedSelectedProposal rejected = resolvedProposal(
                "proposal-2",
                2,
                0.80,
                "driver-2",
                List.of("order-b", "order-a"));
        ResolvedSelectedProposal laterIndependent = resolvedProposal(
                "proposal-3",
                3,
                0.70,
                "driver-3",
                List.of("order-b"));

        ExecutionConflictValidationResult result = new ExecutionConflictValidator()
                .validate(List.of(accepted, rejected, laterIndependent));

        assertEquals(2, result.acceptedProposals().size());
        assertIterableEquals(
                List.of("proposal-1", "proposal-3"),
                result.acceptedProposals().stream()
                        .map(resolved -> resolved.selectedProposal().proposalId())
                        .toList());
        assertIterableEquals(List.of("proposal-2"), result.trace().conflictRejectedProposalIds());
        assertTrue(result.degradeReasons().contains("executor-conflict-validation-failed"));
    }

    private ResolvedSelectedProposal resolvedProposal(String proposalId,
                                                      int selectionRank,
                                                      double selectionScore,
                                                      String driverId,
                                                      List<String> orderIds) {
        return new ResolvedSelectedProposal(
                new SelectedProposal(
                        "selected-proposal/v1",
                        proposalId,
                        selectionRank,
                        selectionScore,
                        List.of("selected")),
                new SelectorCandidate(
                        "selector-candidate/v1",
                        proposalId,
                        "bundle-" + proposalId,
                        orderIds.getFirst(),
                        driverId,
                        orderIds,
                        0.75,
                        0.70,
                        com.routechain.v2.route.RouteProposalSource.HEURISTIC_FAST,
                        "cluster-" + proposalId,
                        false,
                        selectionScore,
                        true,
                        List.of("candidate"),
                        List.of()),
                new RouteProposal(
                        "route-proposal/v1",
                        proposalId,
                        "bundle-" + proposalId,
                        orderIds.getFirst(),
                        driverId,
                        com.routechain.v2.route.RouteProposalSource.HEURISTIC_FAST,
                        List.copyOf(orderIds),
                        8.0,
                        18.0,
                        0.65,
                        true,
                        List.of("route"),
                        List.of()),
                new com.routechain.v2.bundle.BundleCandidate(
                        "bundle-candidate/v1",
                        "bundle-" + proposalId,
                        com.routechain.v2.bundle.BundleProposalSource.DETERMINISTIC_FAMILY,
                        com.routechain.v2.bundle.BundleFamily.COMPACT_CLIQUE,
                        "cluster-" + proposalId,
                        false,
                        List.of(),
                        List.copyOf(orderIds),
                        String.join("|", orderIds),
                        orderIds.getFirst(),
                        "corridor-" + proposalId,
                        0.60,
                        true,
                        List.of()),
                new com.routechain.v2.route.PickupAnchor(
                        "pickup-anchor/v1",
                        "bundle-" + proposalId,
                        String.join("|", orderIds),
                        orderIds.getFirst(),
                        1,
                        0.50,
                        List.of("anchor")),
                new com.routechain.v2.route.DriverCandidate(
                        "driver-candidate/v1",
                        "bundle-" + proposalId,
                        orderIds.getFirst(),
                        driverId,
                        1,
                        9.0,
                        0.55,
                        0.58,
                        List.of("driver"),
                        List.of()));
    }
}

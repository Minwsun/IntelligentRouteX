package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DispatchAssignmentIdentityStabilityTest {

    @Test
    void sameInputYieldsSameAssignmentIdAndChangedRankChangesIdentity() {
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
        DispatchAssignmentBuilder builder = new DispatchAssignmentBuilder();
        SelectedProposal selectedProposal = selectorStage.globalSelectionResult().selectedProposals().getFirst();
        SelectorCandidate selectorCandidate = selectorStage.selectorCandidates().stream()
                .filter(candidate -> candidate.proposalId().equals(selectedProposal.proposalId()))
                .findFirst()
                .orElseThrow();
        RouteProposal routeProposal = routeProposalStage.routeProposals().stream()
                .filter(proposal -> proposal.proposalId().equals(selectedProposal.proposalId()))
                .findFirst()
                .orElseThrow();
        ResolvedSelectedProposal resolved = new SelectedProposalResolver().resolve(
                        selectedProposal,
                        Map.of(selectorCandidate.proposalId(), selectorCandidate),
                        Map.of(routeProposal.proposalId(), routeProposal),
                        routeCandidateStage,
                        context)
                .resolvedProposal()
                .orElseThrow();
        ResolvedSelectedProposal changedRank = new ResolvedSelectedProposal(
                new com.routechain.v2.selector.SelectedProposal(
                        resolved.selectedProposal().schemaVersion(),
                        resolved.selectedProposal().proposalId(),
                        resolved.selectedProposal().selectionRank() + 1,
                        resolved.selectedProposal().selectionScore(),
                        resolved.selectedProposal().reasons()),
                resolved.selectorCandidate(),
                resolved.routeProposal(),
                resolved.bundleCandidate(),
                resolved.pickupAnchor(),
                resolved.driverCandidate());

        DispatchAssignment first = builder.build(resolved, context).assignment().orElseThrow();
        DispatchAssignment second = builder.build(resolved, context).assignment().orElseThrow();
        DispatchAssignment changed = builder.build(changedRank, context).assignment().orElseThrow();

        assertEquals(first.assignmentId(), second.assignmentId());
        assertNotEquals(first.assignmentId(), changed.assignmentId());
    }
}

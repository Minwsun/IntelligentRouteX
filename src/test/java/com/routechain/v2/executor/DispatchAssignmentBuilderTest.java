package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchAssignmentBuilderTest {

    @Test
    void buildsAssignmentFromSelectedProposalSelectorCandidateRouteProposalAndBundleProvenance() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
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

        DispatchAssignmentBuildResult result = builder.build(selectedProposal, selectorCandidate, routeProposal, context);
        DispatchAssignment assignment = result.assignment().orElseThrow();

        assertEquals(selectorCandidate.driverId(), assignment.driverId());
        assertEquals(selectorCandidate.orderIds(), assignment.orderIds());
        assertEquals(routeProposal.stopOrder(), assignment.stopOrder());
        assertEquals(selectorCandidate.clusterId(), assignment.clusterId());
        assertEquals(selectorCandidate.boundaryCross(), assignment.boundaryCross());
        assertEquals(context.readyWindowStart(selectorCandidate.bundleId()), assignment.readyWindowStart());
        assertEquals(context.readyWindowEnd(selectorCandidate.bundleId()), assignment.readyWindowEnd());
        assertTrue(assignment.reasons().contains("selected-by-global-selector"));
        assertTrue(assignment.reasons().contains("executor-assignment-materialized"));
    }
}

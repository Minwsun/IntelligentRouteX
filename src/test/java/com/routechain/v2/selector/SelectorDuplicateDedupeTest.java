package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectorDuplicateDedupeTest {

    @Test
    void exactDuplicateProposalsCollapseBeforeConflictGraphBuild() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        RouteProposal template = RouteTestFixtures.routeProposalStage(properties).routeProposals().get(0);
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                RouteTestFixtures.request().availableDrivers(),
                pairClusterStage,
                bundleStage);
        SelectorCandidateBuilder builder = new SelectorCandidateBuilder(properties);

        RouteProposal lowerDuplicate = SelectorTestFixtures.proposal(
                "proposal-low",
                template.bundleId(),
                template.anchorOrderId(),
                template.driverId(),
                template.source(),
                template.stopOrder(),
                template.projectedPickupEtaMinutes(),
                template.projectedCompletionEtaMinutes(),
                template.routeValue() - 0.10,
                template.feasible());
        RouteProposal higherDuplicate = SelectorTestFixtures.proposal(
                "proposal-high",
                template.bundleId(),
                template.anchorOrderId(),
                template.driverId(),
                template.source(),
                template.stopOrder(),
                template.projectedPickupEtaMinutes(),
                template.projectedCompletionEtaMinutes(),
                template.routeValue(),
                template.feasible());
        SelectorCandidateBuildResult buildResult = builder.build(
                SelectorTestFixtures.routeProposalStage(List.of(lowerDuplicate, higherDuplicate)),
                SelectorTestFixtures.scenarioStage(List.of(
                        SelectorTestFixtures.utility("proposal-low", 0.70),
                        SelectorTestFixtures.utility("proposal-high", 0.85))),
                routeCandidateStage,
                context);
        ConflictGraph conflictGraph = new ConflictGraphBuilder().build(
                buildResult.candidateEnvelopes().stream().map(SelectorCandidateEnvelope::candidate).toList());

        assertEquals(1, buildResult.candidateEnvelopes().size());
        assertEquals("proposal-high", buildResult.candidateEnvelopes().get(0).candidate().proposalId());
        assertEquals(0, conflictGraph.conflictEdgeCount());
    }
}

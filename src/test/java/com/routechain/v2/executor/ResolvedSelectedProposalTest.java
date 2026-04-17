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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedSelectedProposalTest {

    @Test
    void resolvesFullContextForCompatibleSelectedProposalAndFailsWhenARequiredComponentIsMissing() {
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
        SelectedProposal selectedProposal = selectorStage.globalSelectionResult().selectedProposals().getFirst();
        SelectorCandidate selectorCandidate = selectorStage.selectorCandidates().stream()
                .filter(candidate -> candidate.proposalId().equals(selectedProposal.proposalId()))
                .findFirst()
                .orElseThrow();
        RouteProposal routeProposal = routeProposalStage.routeProposals().stream()
                .filter(proposal -> proposal.proposalId().equals(selectedProposal.proposalId()))
                .findFirst()
                .orElseThrow();

        SelectedProposalResolveResult resolved = resolver.resolve(
                selectedProposal,
                Map.of(selectorCandidate.proposalId(), selectorCandidate),
                Map.of(routeProposal.proposalId(), routeProposal),
                routeCandidateStage,
                context);
        SelectedProposalResolveResult missingRoute = resolver.resolve(
                selectedProposal,
                Map.of(selectorCandidate.proposalId(), selectorCandidate),
                Map.of(),
                routeCandidateStage,
                context);

        assertTrue(resolved.resolvedProposal().isPresent());
        assertEquals(selectedProposal.proposalId(), resolved.resolvedProposal().orElseThrow().routeProposal().proposalId());
        assertTrue(missingRoute.resolvedProposal().isEmpty());
        assertTrue(missingRoute.degradeReasons().contains("executor-missing-selected-proposal-context"));
    }
}

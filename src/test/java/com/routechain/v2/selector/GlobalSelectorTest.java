package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalSelectorTest {

    @Test
    void usesGreedyRepairWhenOrtoolsDisabledAndDegradesCleanlyWhenUnavailable() {
        List<SelectorCandidateEnvelope> candidates = List.of(
                SelectorTestFixtures.envelope("proposal-a", "bundle-a", "driver-1", List.of("order-1"), 0.90, 0.85, 0.95, 5.0, true),
                SelectorTestFixtures.envelope("proposal-b", "bundle-b", "driver-2", List.of("order-1"), 0.80, 0.80, 0.88, 6.0, true),
                SelectorTestFixtures.envelope("proposal-c", "bundle-c", "driver-3", List.of("order-2"), 0.75, 0.70, 0.81, 4.0, true));
        ConflictGraph graph = new ConflictGraphBuilder().build(candidates.stream().map(SelectorCandidateEnvelope::candidate).toList());
        GlobalSelector greedySelector = new GlobalSelector(RouteChainDispatchV2Properties.defaults(), new GreedyRepairSelector(), new OrToolsSetPackingSolver());
        RouteChainDispatchV2Properties degradedProperties = RouteChainDispatchV2Properties.defaults();
        degradedProperties.setSelectorOrtoolsEnabled(true);
        GlobalSelector degradedSelector = new GlobalSelector(degradedProperties, new GreedyRepairSelector(), new OrToolsSetPackingSolver());

        GlobalSelectionResult greedyResult = greedySelector.select(candidates, graph).selectionResult();
        GlobalSelectionResult degradedResult = degradedSelector.select(candidates, graph).selectionResult();
        Map<String, SelectorCandidate> candidateById = candidates.stream()
                .map(SelectorCandidateEnvelope::candidate)
                .collect(java.util.stream.Collectors.toMap(SelectorCandidate::proposalId, candidate -> candidate));

        assertEquals(SelectionSolverMode.GREEDY_REPAIR, greedyResult.solverMode());
        assertEquals(SelectionSolverMode.DEGRADED_GREEDY, degradedResult.solverMode());
        assertTrue(degradedResult.degradeReasons().contains("selector-ortools-unavailable"));
        assertTrue(degradedResult.selectedProposals().stream().allMatch(selected -> graph.edges().stream()
                .filter(edge -> edge.leftProposalId().equals(selected.proposalId()) || edge.rightProposalId().equals(selected.proposalId()))
                .allMatch(edge -> degradedResult.selectedProposals().stream().noneMatch(other ->
                        !other.proposalId().equals(selected.proposalId())
                                && (other.proposalId().equals(edge.leftProposalId()) || other.proposalId().equals(edge.rightProposalId()))))));
        assertTrue(greedyResult.selectedProposals().stream().allMatch(selected -> candidateById.containsKey(selected.proposalId())));
    }
}

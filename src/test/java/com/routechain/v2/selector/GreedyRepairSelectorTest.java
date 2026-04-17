package com.routechain.v2.selector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyRepairSelectorTest {

    @Test
    void selectsConflictFreeSubsetPrefersHigherScoreAndIsDeterministic() {
        List<SelectorCandidateEnvelope> candidates = List.of(
                SelectorTestFixtures.envelope("proposal-a", "bundle-a", "driver-1", List.of("order-1"), 0.90, 0.85, 0.95, 5.0, true),
                SelectorTestFixtures.envelope("proposal-b", "bundle-b", "driver-2", List.of("order-1"), 0.80, 0.80, 0.88, 6.0, true),
                SelectorTestFixtures.envelope("proposal-c", "bundle-c", "driver-3", List.of("order-2"), 0.75, 0.70, 0.81, 4.0, true));
        ConflictGraph graph = new ConflictGraphBuilder().build(candidates.stream().map(SelectorCandidateEnvelope::candidate).toList());
        GreedyRepairSelector selector = new GreedyRepairSelector();

        SelectorSelectionOutcome first = selector.select(candidates, graph, SelectionSolverMode.GREEDY_REPAIR, true);
        SelectorSelectionOutcome second = selector.select(candidates, graph, SelectionSolverMode.GREEDY_REPAIR, true);

        assertEquals(List.of("proposal-a", "proposal-c"), first.selectionResult().selectedProposals().stream().map(SelectedProposal::proposalId).toList());
        assertEquals(first.selectionResult().selectedProposals(), second.selectionResult().selectedProposals());
        assertTrue(first.selectionResult().selectedProposals().stream().noneMatch(selected -> selected.proposalId().equals("proposal-b")));
    }
}

package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalSelectorOrToolsTest {

    @Test
    void usesOrtoolsWhenEnabledAndObjectiveIsNotWorseThanGreedy() {
        List<SelectorCandidateEnvelope> candidates = List.of(
                SelectorTestFixtures.envelope("proposal-a", "bundle-a", "driver-1", List.of("order-1"), 0.90, 0.70, 0.90, 6.0, true),
                SelectorTestFixtures.envelope("proposal-b", "bundle-b", "driver-2", List.of("order-2"), 0.88, 0.75, 0.89, 5.0, true),
                SelectorTestFixtures.envelope("proposal-c", "bundle-c", "driver-3", List.of("order-1", "order-2"), 0.95, 0.93, 1.95, 7.0, true),
                SelectorTestFixtures.envelope("proposal-d", "bundle-d", "driver-4", List.of("order-3"), 0.70, 0.65, 0.70, 4.0, true));
        ConflictGraph graph = new ConflictGraphBuilder().build(candidates.stream().map(SelectorCandidateEnvelope::candidate).toList());

        RouteChainDispatchV2Properties ortoolsProperties = RouteChainDispatchV2Properties.defaults();
        ortoolsProperties.setSelectorOrtoolsEnabled(true);
        ortoolsProperties.getSelector().getOrtools().setTimeout(Duration.ofSeconds(2));
        GlobalSelectionResult ortoolsResult = new GlobalSelector(
                ortoolsProperties,
                new GreedyRepairSelector(),
                new OrToolsSetPackingSolver(ortoolsProperties))
                .select(candidates, graph)
                .selectionResult();

        GlobalSelectionResult greedyResult = new GlobalSelector(
                RouteChainDispatchV2Properties.defaults(),
                new GreedyRepairSelector(),
                new OrToolsSetPackingSolver())
                .select(candidates, graph)
                .selectionResult();

        assertEquals(SelectionSolverMode.ORTOOLS, ortoolsResult.solverMode());
        assertConflictFree(ortoolsResult, candidates);
        assertTrue(ortoolsResult.objectiveValue() >= greedyResult.objectiveValue());
    }

    private void assertConflictFree(GlobalSelectionResult selectionResult, List<SelectorCandidateEnvelope> candidates) {
        java.util.Map<String, SelectorCandidate> byProposalId = candidates.stream()
                .map(SelectorCandidateEnvelope::candidate)
                .collect(java.util.stream.Collectors.toMap(SelectorCandidate::proposalId, candidate -> candidate));
        Set<String> seenDrivers = new HashSet<>();
        Set<String> seenOrders = new HashSet<>();
        for (SelectedProposal selectedProposal : selectionResult.selectedProposals()) {
            SelectorCandidate candidate = byProposalId.get(selectedProposal.proposalId());
            assertTrue(seenDrivers.add(candidate.driverId()));
            assertTrue(candidate.orderIds().stream().allMatch(seenOrders::add));
        }
    }
}

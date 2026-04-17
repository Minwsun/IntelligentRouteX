package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalSelectorDegradeTest {

    @Test
    void usesGreedyRepairWhenOrtoolsDisabled() {
        List<SelectorCandidateEnvelope> candidates = List.of(
                SelectorTestFixtures.envelope("proposal-a", "bundle-a", "driver-1", List.of("order-1"), 0.90, 0.85, 0.95, 5.0, true),
                SelectorTestFixtures.envelope("proposal-b", "bundle-b", "driver-2", List.of("order-2"), 0.80, 0.80, 0.88, 6.0, true));
        ConflictGraph graph = new ConflictGraphBuilder().build(candidates.stream().map(SelectorCandidateEnvelope::candidate).toList());

        GlobalSelectionResult greedyResult = new GlobalSelector(
                RouteChainDispatchV2Properties.defaults(),
                new GreedyRepairSelector(),
                unavailable("selector-ortools-failed"))
                .select(candidates, graph)
                .selectionResult();

        assertEquals(SelectionSolverMode.GREEDY_REPAIR, greedyResult.solverMode());
    }

    @Test
    void degradesToGreedyWhenSolverReturnsUnavailableTimeoutOrFailure() {
        assertDegradedReason("selector-ortools-unavailable");
        assertDegradedReason("selector-ortools-timeout");
        assertDegradedReason("selector-ortools-failed");
    }

    private void assertDegradedReason(String degradeReason) {
        List<SelectorCandidateEnvelope> candidates = List.of(
                SelectorTestFixtures.envelope("proposal-a", "bundle-a", "driver-1", List.of("order-1"), 0.90, 0.85, 0.95, 5.0, true),
                SelectorTestFixtures.envelope("proposal-b", "bundle-b", "driver-2", List.of("order-1"), 0.80, 0.80, 0.88, 6.0, true),
                SelectorTestFixtures.envelope("proposal-c", "bundle-c", "driver-3", List.of("order-2"), 0.75, 0.70, 0.81, 4.0, true));
        ConflictGraph graph = new ConflictGraphBuilder().build(candidates.stream().map(SelectorCandidateEnvelope::candidate).toList());
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setSelectorOrtoolsEnabled(true);

        GlobalSelectionResult degradedResult = new GlobalSelector(
                properties,
                new GreedyRepairSelector(),
                unavailable(degradeReason))
                .select(candidates, graph)
                .selectionResult();

        assertEquals(SelectionSolverMode.DEGRADED_GREEDY, degradedResult.solverMode());
        assertTrue(degradedResult.degradeReasons().contains(degradeReason));
    }

    private SelectorSolver unavailable(String degradeReason) {
        return (selectorCandidates, conflictGraph) -> new SelectorSolverResult(Optional.empty(), List.of(degradeReason));
    }
}

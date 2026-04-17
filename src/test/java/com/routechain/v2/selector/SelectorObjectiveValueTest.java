package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectorObjectiveValueTest {

    @Test
    void objectiveValueAlwaysMatchesSumOfSelectedSelectionScoresAcrossModes() {
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

        assertEquals(greedyResult.selectedProposals().stream().mapToDouble(SelectedProposal::selectionScore).sum(), greedyResult.objectiveValue(), 1e-9);
        assertEquals(degradedResult.selectedProposals().stream().mapToDouble(SelectedProposal::selectionScore).sum(), degradedResult.objectiveValue(), 1e-9);
    }
}

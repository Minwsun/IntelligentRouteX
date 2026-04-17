package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.selector.SelectorCandidate;
import com.routechain.v2.selector.SelectionSolverMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreOrToolsSliceTest {

    @Test
    void runtimeKeepsTwelveStagesAndExecutorStillRunsAfterOrtoolsSelection() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setSelectorOrtoolsEnabled(true);
        properties.getSelector().getOrtools().setTimeout(Duration.ofSeconds(2));
        DispatchV2Core core = TestDispatchV2Factory.core(properties);

        DispatchV2Result result = core.dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertEquals(SelectionSolverMode.ORTOOLS, result.globalSelectionResult().solverMode());
        assertFalse(result.assignments().isEmpty());
        assertTrue(result.globalSelectionResult().selectedCount() > 0);

        Set<String> selectedDrivers = new HashSet<>();
        Set<String> selectedOrders = new HashSet<>();
        for (var selectedProposal : result.globalSelectionResult().selectedProposals()) {
            SelectorCandidate candidate = result.selectorCandidates().stream()
                    .filter(current -> current.proposalId().equals(selectedProposal.proposalId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(selectedDrivers.add(candidate.driverId()));
            assertTrue(candidate.orderIds().stream().allMatch(selectedOrders::add));
        }
    }
}

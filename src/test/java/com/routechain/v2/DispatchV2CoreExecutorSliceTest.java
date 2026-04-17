package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.executor.DispatchAssignment;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreExecutorSliceTest {

    @Test
    void enabledPathReturnsExecutorOutputsAndAssignmentsRemainConflictFree() {
        DispatchV2Core core = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults());

        DispatchV2Result result = core.dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertFalse(result.fallbackUsed());
        assertFalse(result.assignments().isEmpty());
        assertEquals(result.assignments().getFirst().proposalId(), result.selectedRouteId());
        assertTrue(result.dispatchExecutionSummary().assignmentCount() > 0);

        Set<String> drivers = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (DispatchAssignment assignment : result.assignments()) {
            assertTrue(drivers.add(assignment.driverId()));
            assertTrue(assignment.orderIds().stream().allMatch(orders::add));
        }
    }
}

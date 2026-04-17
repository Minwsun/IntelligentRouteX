package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchExecutionSummaryTest {

    @Test
    void summaryCountsReflectExecutedAssignmentsDriversOrdersAndActionTypes() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchExecutorStage stage = RouteTestFixtures.executorStage(properties);

        assertEquals(stage.assignments().size(), stage.dispatchExecutionSummary().assignmentCount());
        assertEquals(stage.assignments().stream().map(DispatchAssignment::driverId).distinct().count(), stage.dispatchExecutionSummary().executedDriverCount());
        assertEquals(stage.assignments().stream().flatMap(assignment -> assignment.orderIds().stream()).collect(java.util.stream.Collectors.toSet()).size(), stage.dispatchExecutionSummary().executedOrderCount());
        assertEquals(stage.assignments().size(), stage.dispatchExecutionSummary().actionTypeCounts().getOrDefault(ExecutionActionType.ASSIGN_DRIVER, 0));
        assertTrue(stage.dispatchExecutionSummary().selectedProposalCount() >= stage.dispatchExecutionSummary().assignmentCount());
    }
}

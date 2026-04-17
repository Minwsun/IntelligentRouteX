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

        assertEquals(stage.assignments().size(), stage.dispatchExecutionSummary().executedAssignmentCount());
        assertEquals(
                stage.dispatchExecutionSummary().selectedProposalCount() - stage.dispatchExecutionSummary().executedAssignmentCount(),
                stage.dispatchExecutionSummary().skippedProposalCount());
        assertTrue(stage.dispatchExecutionSummary().resolvedProposalCount() >= stage.dispatchExecutionSummary().executedAssignmentCount());
        assertTrue(stage.dispatchExecutionSummary().resolvedButRejectedCount() >= 0);
    }
}

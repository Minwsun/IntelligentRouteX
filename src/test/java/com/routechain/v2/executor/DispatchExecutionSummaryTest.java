package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DispatchExecutionSummaryTest {

    @Test
    void summaryCountsReflectSelectedResolvedExecutedAndRejectedSemantics() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchExecutorStage stage = RouteTestFixtures.executorStage(properties);

        assertEquals(stage.assignments().size(), stage.dispatchExecutionSummary().executedAssignmentCount());
        assertEquals(
                stage.dispatchExecutionSummary().selectedProposalCount() - stage.dispatchExecutionSummary().executedAssignmentCount(),
                stage.dispatchExecutionSummary().skippedProposalCount());
        assertTrue(stage.dispatchExecutionSummary().resolvedProposalCount() >= stage.dispatchExecutionSummary().executedAssignmentCount());
        assertTrue(stage.dispatchExecutionSummary().resolvedButRejectedCount() >= 0);
        assertTrue(stage.dispatchExecutionSummary().resolvedButRejectedCount()
                <= stage.dispatchExecutionSummary().skippedProposalCount());
    }

    @Test
    void resolvedButRejectedCountIsNarrowerThanSkippedWhenMissingContextAlsoSkipsProposals() {
        DispatchExecutionSummary summary = new DispatchExecutionSummary(
                "dispatch-execution-summary/v2",
                5,
                4,
                2,
                3,
                1,
                java.util.List.of("executor-missing-selected-proposal-context", "executor-conflict-validation-failed"));

        assertEquals(
                summary.selectedProposalCount() - summary.executedAssignmentCount(),
                summary.skippedProposalCount());
        assertNotEquals(summary.skippedProposalCount(), summary.resolvedButRejectedCount());
        assertTrue(summary.resolvedButRejectedCount() < summary.skippedProposalCount());
    }
}

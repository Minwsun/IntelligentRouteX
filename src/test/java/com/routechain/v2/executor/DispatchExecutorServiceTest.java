package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchExecutorServiceTest {

    @Test
    void consumesSelectorOutputAndProducesAssignmentsWithoutExternalExecutionSideEffects() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        var selectorStage = RouteTestFixtures.selectorStage(properties);
        DispatchExecutorService service = RouteTestFixtures.executorService(properties);

        DispatchExecutorStage stage = service.evaluate(
                RouteTestFixtures.request(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                selectorStage);

        assertFalse(stage.assignments().isEmpty());
        assertNotNull(stage.dispatchExecutionSummary());
        assertTrue(stage.assignments().stream().allMatch(assignment -> assignment.actionType() == ExecutionActionType.ASSIGN_DRIVER));
        assertEquals(stage.assignments().size(), stage.dispatchExecutionSummary().executedAssignmentCount());
    }
}

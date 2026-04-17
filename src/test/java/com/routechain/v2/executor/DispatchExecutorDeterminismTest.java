package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchExecutorDeterminismTest {

    @Test
    void sameInputYieldsSameAssignmentOrderingAndSelectedRouteId() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        var selectorStage = RouteTestFixtures.selectorStage(properties);
        DispatchExecutorService service = RouteTestFixtures.executorService(properties);

        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchExecutorStage first = service.evaluate(RouteTestFixtures.request(), pairClusterStage, bundleStage, routeCandidateStage, routeProposalStage, selectorStage);
        DispatchExecutorStage second = service.evaluate(RouteTestFixtures.request(), pairClusterStage, bundleStage, routeCandidateStage, routeProposalStage, selectorStage);

        assertEquals(first.assignments(), second.assignments());
        assertEquals(first.dispatchExecutionSummary(), second.dispatchExecutionSummary());
    }
}

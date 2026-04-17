package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DispatchSelectorServiceTest {

    @Test
    void consumesRobustUtilitiesAndProducesSelectorOutputsWithoutExecutorState() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        var scenarioStage = RouteTestFixtures.scenarioStage(properties);
        DispatchSelectorService service = RouteTestFixtures.selectorService(properties);

        DispatchSelectorStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage,
                routeProposalStage,
                scenarioStage);

        assertFalse(stage.selectorCandidates().isEmpty());
        assertNotNull(stage.conflictGraph());
        assertNotNull(stage.globalSelectionResult());
        assertNotNull(stage.globalSelectorSummary());
    }
}

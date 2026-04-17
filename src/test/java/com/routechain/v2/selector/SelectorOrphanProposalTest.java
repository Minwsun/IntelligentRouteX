package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorOrphanProposalTest {

    @Test
    void proposalMissingRequiredUpstreamContextDoesNotBecomeSelectorCandidate() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        var routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                RouteTestFixtures.request().availableDrivers(),
                pairClusterStage,
                bundleStage);
        SelectorCandidateBuilder builder = new SelectorCandidateBuilder(properties);

        SelectorCandidateBuildResult buildResult = builder.build(
                routeProposalStage,
                SelectorTestFixtures.scenarioStage(java.util.List.of()),
                routeCandidateStage,
                context);

        assertEquals(0, buildResult.candidateEnvelopes().size());
        assertTrue(buildResult.degradeReasons().contains("selector-missing-upstream-context"));
    }
}

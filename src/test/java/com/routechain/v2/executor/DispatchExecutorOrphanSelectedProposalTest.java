package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectedProposal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchExecutorOrphanSelectedProposalTest {

    @Test
    void missingSelectorOrRouteContextDoesNotEmitAssignmentAndRecordsDegradeReason() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        DispatchSelectorStage selectorStage = RouteTestFixtures.selectorStage(properties);
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                RouteTestFixtures.request().availableDrivers(),
                pairClusterStage,
                bundleStage);
        DispatchAssignmentBuilder builder = new DispatchAssignmentBuilder();
        SelectedProposal selectedProposal = selectorStage.globalSelectionResult().selectedProposals().getFirst();

        DispatchAssignmentBuildResult missingSelector = builder.build(selectedProposal, null, null, context);

        assertTrue(missingSelector.assignment().isEmpty());
        assertTrue(missingSelector.degradeReasons().contains("executor-missing-upstream-context"));
    }
}

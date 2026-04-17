package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestRouteFinderClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchRouteProposalServiceRouteFinderIntegrationTest {

    @Test
    void routeFinderAddsMlProposalAndRefinedSourcesWithoutRemovingDeterministicSources() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getRoutefinder().setEnabled(true);
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalService service = RouteTestFixtures.routeProposalService(
                properties,
                new NoOpTabularScoringClient(),
                TestRouteFinderClient.applied());

        DispatchRouteProposalStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage);

        assertTrue(stage.routeProposals().stream().anyMatch(proposal -> proposal.source() == RouteProposalSource.HEURISTIC_FAST));
        assertTrue(stage.routeProposals().stream().anyMatch(proposal -> proposal.source() == RouteProposalSource.ML_PROPOSAL));
        assertTrue(stage.routeProposals().stream().anyMatch(proposal -> proposal.source() == RouteProposalSource.ML_REFINED));
        assertTrue(stage.mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("routefinder-local")));
    }

    @Test
    void unavailableRouteFinderPreservesDeterministicProposalPool() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getRoutefinder().setEnabled(true);
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalService service = RouteTestFixtures.routeProposalService(
                properties,
                new NoOpTabularScoringClient(),
                TestRouteFinderClient.notApplied("routefinder-unavailable"));

        DispatchRouteProposalStage stage = service.evaluate(
                RouteTestFixtures.request(),
                RouteTestFixtures.etaContext(),
                pairClusterStage,
                bundleStage,
                routeCandidateStage);

        assertFalse(stage.routeProposals().isEmpty());
        assertTrue(stage.routeProposals().stream().noneMatch(proposal -> proposal.source() == RouteProposalSource.ML_PROPOSAL || proposal.source() == RouteProposalSource.ML_REFINED));
        assertTrue(stage.degradeReasons().contains("routefinder-ml-unavailable"));
    }
}
